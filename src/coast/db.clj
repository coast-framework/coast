(ns coast.db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [coast.env :as env]
            [coast.queries :as queries]
            [coast.utils :as utils])
  (:refer-clojure :exclude [drop]))

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
      (unique-index-error? s) (utils/throw+ (fmt-unique-index-error s))
      :else (throw e))))

(defmacro transact! [f]
  `(try
     ~f
     (catch Exception e#
       (throw-db-exception e#))))

(defn connection []
  (let [db-url (or (env/env :database-url)
                   (env/env :db-spec-or-url))]
    (if (string/blank? db-url)
      (throw (Exception. "Your database connection string is blank. Set the DATABASE_URL or DB_SPEC_OR_URL environment variable"))
      {:connection (jdbc/get-connection db-url)})))

(defn admin-connection []
  (let [db-url (or (env/env :admin-db-spec-or-url)
                   (env/env :admin-database-url)
                   "postgres://localhost:5432/postgres")]
    (if (string/blank? db-url)
      (throw (Exception. "Your admin database connection string is blank. Set the ADMIN_DB_SPEC_OR_URL environment variable"))
      {:connection (jdbc/get-connection db-url)})))

(defn sql-vec? [v]
  (and (vector? v)
       (string? (first v))
       (not (string/blank? (first v)))))

(defn execute! [db sql]
  (jdbc/execute! db sql))

(defn exec [db sql]
  (jdbc/with-db-connection [conn db]
    (with-open [s (.createStatement (jdbc/db-connection conn))]
      (.addBatch s sql)
      (seq (.executeBatch s)))))

(defn query
  ([conn v opts]
   (if (and (sql-vec? v) (map? opts))
     (transact!
       (jdbc/with-db-connection [db-conn conn]
         (jdbc/query db-conn v {:row-fn (partial utils/map-keys utils/kebab)})))
     '()))
  ([conn v]
   (query conn v {})))

(defn create-root-var [name value]
  ; shamelessly stolen from yesql
  (intern *ns*
          (with-meta (symbol name)
                     (meta value))
          value))

(defn query-fn [{:keys [sql f throw-on-nil?]}]
  (fn [& [m]]
    (let [val (->> (queries/sql-vec sql m)
                   (query (connection))
                   (f))]
      (if (and (nil? val)
               (true? throw-on-nil?))
        (utils/throw-not-found)
        val))))

(defn query-fns [filename]
   (doall (->> (queries/slurp-resource filename)
               (queries/parse)
               (map #(create-root-var (:name %) (query-fn %))))))

(defmacro defq
  ([n filename]
   `(->> (queries/slurp-resource ~filename)
         (filter #(= (:name %) ~(str n)))
         (first)
         (query-fn)
         (create-root-var ~(str n))))
  ([filename]
   `(query-fns ~filename)))

(defmacro defq! [n filename]
  `(as-> (queries/slurp-resource ~filename) %
         (filter #(= (:name %) ~n) %)
         (first %)
         (assoc % :throw-on-nil? true)
         (query-fn %)
         (create-root-var (str ~n) %)))

(defq "sql/schema.sql")

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
