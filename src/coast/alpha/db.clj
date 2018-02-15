(ns coast.alpha.db
  (:require [clojure.java.jdbc :as sql]
            [clojure.string :as string]
            [oksql.core :as oksql]
            [coast.utils :as utils]
            [coast.alpha.env :as env])
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
      (unique-index-error? s) (utils/throw+ (fmt-unique-index-error s))
      :else (throw e))))

(defmacro transact! [f]
  `(try
     ~f
     (catch Exception e#
       (throw-db-exception e#))))

(defn connection []
  (let [db-url (or (env/env :db-spec-or-url)
                   (env/env :database-url))]
    (if (string/blank? db-url)
      (throw (Exception. "Your database connection string is blank. Set the DATABASE_URL environment variable"))
      {:connection (sql/get-connection db-url)})))

(defn admin-connection []
  (let [db-url (or (env/env :admin-db-spec-or-url)
                   "postgres://localhost:5432")]
    (if (string/blank? db-url)
      (throw (Exception. "Your admin database connection string is blank. Set the ADMIN_DB_SPEC_OR_URL environment variable"))
      {:connection (sql/get-connection db-url)})))

(defn query
  ([k m]
   (oksql/query (connection) k m))
  ([k]
   (query k {})))

(defn query!
  ([k m]
   (let [results (oksql/query (connection) k m)]
     (if (or (nil? results)
             (empty? results))
       (utils/throw+ {:coast/error "Query results were empty"
                      :coast/error-type :not-found})
       results)))
  ([k]
   (query! k {})))

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
