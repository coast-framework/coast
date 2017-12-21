(ns coast.db
  (:require [environ.core :as environ]
            [clojure.java.jdbc :as sql]
            [clojure.string :as string]
            [oksql.core :as oksql]
            [coast.utils :as utils])
  (:refer-clojure :exclude [drop update]))

(defn connection []
  {:connection (sql/get-connection (environ/env :database-url))})

(defn query
  ([k m]
   (oksql/query (connection) k m))
  ([k]
   (query k {})))

(defn insert [k m]
  (oksql/insert (connection) k m))

(defn update [k m where where-map]
  (oksql/update (connection) k m where where-map))

(defn delete [k where where-map]
  (oksql/delete (connection) k where where-map))

(defn exec [db sql]
  (sql/with-db-connection [conn db]
    (with-open [s (.createStatement (sql/db-connection conn))]
      (.addBatch s sql)
      (seq (.executeBatch s)))))

(defn create [name]
  (let [name (if utils/prod? (str name "_prod") (str name "_dev"))
        db {:connection (sql/get-connection "postgres://localhost:5432/postgres")}
        [_ error] (-> (exec db (str "create database " name))
                      (utils/try!))]
    (if (nil? error)
      (println "Database" name "created successfully")
      (utils/printerr "Database could not be created"
                error))))

(defn drop [name]
  (let [name (if utils/prod? (str name "_prod") (str name "_dev"))
        db {:connection (sql/get-connection "postgres://localhost:5432/postgres")}
        [_ error] (-> (exec db (str "drop database " name))
                      (utils/try!))]
    (if (nil? error)
      (println "Database" name "dropped successfully")
      (utils/printerr "Database could not be dropped"
                      error))))

(defn get-cols [table]
  (let [sql ["select column_name from information_schema.columns where table_name = ?" table]]
    (sql/query (connection) sql)))
