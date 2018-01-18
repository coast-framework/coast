(ns coast.db
  (:require [environ.core :as environ]
            [clojure.java.jdbc :as sql]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [oksql.core :as oksql]
            [coast.utils :as utils])
  (:refer-clojure :exclude [drop update]))

(defn unique-index-error? [error]
  (when (not (nil? error))
    (string/includes? error "duplicate key value violates unique constraint")))

(defn fmt-unique-index-error [s]
  (let [column (->> (re-matches #"(?s)ERROR: duplicate key value violates unique constraint.*Detail: Key \((.*)\)=\((.*)\).*" s)
                    (clojure.core/drop 1)
                    (first))]
    {(keyword column) (str (utils/humanize column) " is already taken")}))

(defn throw-db-exception [e]
  (let [s (.getMessage e)]
    (cond
      (unique-index-error? s) (throw
                                (ex-info "Unique index error"
                                         (fmt-unique-index-error s)))
      :else (throw e))))

(defmacro transact! [f]
  `(try
     ~f
     (catch Exception e#
       (throw-db-exception e#))))

(defn connection []
  {:connection (sql/get-connection (or (environ/env :db-spec-or-url) (environ/env :database-url)))})

(defn admin-connection []
  {:connection (sql/get-connection (or (environ/env :admin-db-spec-or-url) "postgres://localhost:5432/postgres"))})

(defn query
  ([k m]
   (oksql/query (connection) k m))
  ([k]
   (query k {})))

(defn insert [k m]
  (-> (oksql/insert (connection) k m)
      (transact!)))

(defn update [k m where where-map]
  (-> (oksql/update (connection) k m where where-map)
      (transact!)))

(defn delete [k where where-map]
  (-> (oksql/delete (connection) k where where-map)
      (transact!)))

(defn exec [db sql]
  (sql/with-db-connection [conn db]
    (with-open [s (.createStatement (sql/db-connection conn))]
      (.addBatch s sql)
      (seq (.executeBatch s)))))

(defn create [name]
  (let [name (if utils/prod? (str name "_prod") (str name "_dev"))
        db (admin-connection)
        [_ error] (-> (exec db (str "create database " name))
                      (utils/try!))]
    (if (nil? error)
      (println "Database" name "created successfully")
      (utils/printerr "Database could not be created"
                error))))

(defn drop [name]
  (let [name (if utils/prod? (str name "_prod") (str name "_dev"))
        db (admin-connection)
        [_ error] (-> (exec db (str "drop database " name))
                      (utils/try!))]
    (if (nil? error)
      (println "Database" name "dropped successfully")
      (utils/printerr "Database could not be dropped"
                      error))))

(defn get-cols [table]
  (let [sql ["select column_name from information_schema.columns where table_name = ?" table]]
    (sql/query (connection) sql)))
