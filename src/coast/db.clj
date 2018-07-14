(ns coast.db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [coast.env :as env]
            [coast.db.queries :as queries]
            [coast.db.transact :as db.transact]
            [coast.db.schema :as db.schema]
            [coast.db.connection :refer [connection admin-db-url]]
            [coast.db.query :as db.query]
            [coast.db.errors :as db.errors]
            [coast.utils :as utils])
  (:import (java.io File))
  (:refer-clojure :exclude [drop update]))

(defn exec [db sql]
  (jdbc/with-db-connection [conn db]
    (with-open [s (.createStatement (jdbc/db-connection conn))]
      (.addBatch s sql)
      (seq (.executeBatch s)))))

(defn sql-vec? [v]
  (and (vector? v)
       (string? (first v))
       (not (string/blank? (first v)))))

(defn query
  ([conn v opts]
   (if (and (sql-vec? v) (map? opts))
     (jdbc/query (connection) v (merge {:keywordize? true
                                        :identifiers utils/kebab} opts))
     (empty list)))
  ([conn v]
   (query conn v {})))

(defn create-root-var [name value]
  ; shamelessly stolen from yesql
  (intern *ns*
          (with-meta (symbol name)
                     (meta value))
          value))

(defn query-fn [{:keys [sql f]}]
  (fn [& [m]]
    (->> (queries/sql-vec sql m)
         (query (connection))
         (f))))

(defn query-fns [filename]
   (doall (->> (queries/slurp-resource filename)
               (queries/parse)
               (map #(assoc % :ns *ns*))
               (map #(create-root-var (:name %) (query-fn %))))))

(defmacro defq
  ([n filename]
   `(let [q-fn# (-> (queries/query ~(str n) ~filename)
                    (assoc :ns *ns*)
                    (query-fn))]
      (create-root-var ~(str n) q-fn#)))
  ([filename]
   `(query-fns ~filename)))

(defn first! [coll]
  (or (first coll)
      (throw (ex-info "Record not found" {:type :404}))))

(defq "sql/schema.sql")

(defn admin-connection []
  {:connection (jdbc/get-connection (admin-db-url))})

(defn create [db-name]
  (let [db-name (format "%s_%s" db-name (env/env :coast-env))
        sql (format "create database %s" db-name)]
    (exec (admin-connection) sql)
    (println "Database" db-name "created successfully")))

(defn drop [db-name]
  (let [db-name (format "%s_%s" db-name (env/env :coast-env))
        sql (format "drop database %s" db-name)]
    (exec (admin-connection) sql)
    (println "Database" db-name "dropped successfully")))

(defn single [coll]
  (if (and (= 1 (count coll))
           (coll? coll))
    (first coll)
    coll))

(defn qualify-col [s]
  (let [parts (string/split s #"_")
        k-ns (first parts)
        k-n (->> (rest parts)
                 (string/join "-"))]
    (keyword k-ns k-n)))

(defn qualify-map [k-ns m]
  (->> (map (fn [[k v]] [(keyword k-ns (name k)) v]) m)
       (into (empty m))))

(defn q
  ([v params]
   (query (connection)
          (db.query/sql-vec v params)
          {:keywordize? false
           :identifiers qualify-col}))
  ([v]
   (q v nil)))

(defn transact [m]
  (let [k-ns (-> m keys first namespace)
        schema (coast.db.schema/fetch)
        v (db.transact/sql-vec schema m)]
    (->> (query (connection) v)
         (map #(qualify-map k-ns %))
         (single))))

(defn delete [arg]
  (let [v (db.transact/delete-vec arg)
        k-ns (if (sequential? arg)
               (-> arg first keys first namespace utils/snake)
               (-> arg keys first namespace utils/snake))]
    (->> (query (connection) v)
         (map #(qualify-map k-ns %))
         (single))))

(defn pull [v ident]
  (first (q [:pull v :where ident])))

(defmacro maybe [f]
  `(try
     [~f nil]
    (catch org.postgresql.util.PSQLException e#
      (let [error-map# (db.errors/error-map e#)]
        (if (empty? error-map#)
          (throw e#)
          [nil error-map#])))))
