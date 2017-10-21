(ns coast.db
  (:require [environ.core :as environ]
            [clojure.java.jdbc :as sql]
            [clojure.string :as string]
            [oksql.core :as oksql])
  (:refer-clojure :exclude [drop update])
  (:import (java.io File)))

(def conn {:connection (sql/get-connection (environ/env :database-url))})

(defn query
  ([k m]
   (oksql/query conn k m))
  ([k]
   (query k {})))

(def insert (partial oksql/insert conn))
(def update (partial oksql/update conn))
(def delete (partial oksql/delete conn))

(defn exec [db sql]
  (sql/with-db-connection [conn db]
    (with-open [s (.createStatement (sql/db-connection conn))]
      (.addBatch s sql)
      (seq (.executeBatch s)))))

(defn create [name]
  (let [db {:connection (sql/get-connection "postgres://localhost:5432/postgres")}]
    (exec db (str "create database " name))))

(defn drop [name]
  (let [db {:connection (sql/get-connection "postgres://localhost:5432/postgres")}]
    (exec db (str "drop database " name))))

(defn get-cols [table]
  (let [sql ["select column_name from information_schema.columns where table_name = ?" table]]
    (sql/query conn sql)))

(defn in? [coll elm]
  (some #(= elm %) coll))

(defn update-cols? [col]
  (not (in? ["id" "created_at"] col)))

(def sql-dir "resources/sql")

(defn sql-file-path [table]
  (str sql-dir "/" table ".sql"))

(defn crud [table]
  (let [sql-file (sql-file-path table)
        rows (get-cols table)
        cols (map :column_name rows)
        update-cols (filter update-cols? cols)
        cols-string (string/join ", " cols)
        params (map #(str ":" %) cols)
        params-string (string/join ", " params)
        update-params (map #(str ":" %) update-cols)
        update-map (apply assoc {} (interleave update-cols update-params))
        updates (map #(str (first %) " = " (second %)) update-map)
        update-string (string/join ", " updates)
        insert (str
                 "-- name: insert"
                 "\n-- fn: first"
                 "\ninsert into " table " (" (string/join ", " update-cols) ") "
                 "\nvalues (" (string/join ", " update-params) ")"
                 "\nreturning *")
        update (str
                 "-- name: update"
                 "\n-- fn: first"
                 "\nupdate " table
                 "\nset " update-string
                 "\nwhere id = :id"
                 "\nreturning *")
        find (str
               "-- name: find-by-id"
               "\n-- fn: first"
               "\nselect *"
               "\nfrom " table
               "\nwhere id = :id")
        delete (str
                 "-- name: delete"
                 "\n-- fn: first"
                 "\ndelete from " table
                 "\nwhere id = :id"
                 "\nreturning *")
        all (str
              "-- name: all"
              "\nselect *"
              "\nfrom " table
              "\norder by created_at desc")
        where (str
                "-- name: where"
                "\nwhere id = :id"
                "\nreturning *")
        contents (string/join "\n\n" [all find insert update delete where])
        _ (.mkdirs (File. sql-dir))
        _ (spit sql-file contents)])
  (println (str "Created resources/sql/" table ".sql")))
