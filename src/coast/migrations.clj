(ns coast.migrations
  (:require [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.java.jdbc :as sql]
            [coast.db :as db]
            [coast.utils :as utils])
  (:import (java.text SimpleDateFormat)
           (java.util Date)
           (java.io File)))

(defn fmt-date [date]
  (when (instance? Date date)
    (.format (SimpleDateFormat. "yyyyMMddHHmmss") date)))

(def migrations-dir "resources/migrations")
(def ragtime-format-edn "{:up [\"\"]\n :down [\"\"]}")

(defn ragtime-conn []
  {:datastore  (jdbc/sql-database db/conn)
   :migrations (jdbc/load-resources "migrations")})

(defn migrate []
  (let [[_ error] (utils/try! (repl/migrate (ragtime-conn)))]
    (when (not (nil? error))
      (println "-- Migration failed --------------------")
      (println "")
      (println error))))

(defn rollback []
  (let [[_ error] (utils/try! (repl/rollback (ragtime-conn)))]
    (when (not (nil? error))
      (println "-- Rollback failed --------------------")
      (println "")
      (println error))))

(defn migration-file-path [name]
  (when (and
          ((comp not nil?) name)
          ((comp not empty?) name)
          (string? name))
    (str migrations-dir "/" (-> (new Date) fmt-date) "_" (string/replace name #"\s+|-+|_+" "_") ".edn")))

(defn column [arg]
  (let [[name type] (string/split arg #":")]
    (str name " " type)))

(defn create-table-contents [name args]
  (let [table (-> (string/split name #"-")
                  (last))
        sql (str "create table " table " (")
        columns (map column args)
        columns (-> columns
                    (conj "id serial primary key")
                    vec
                    (conj "created_at timestamp without time zone default (now() at time zone 'utc')"))
        column-string (string/join ", " columns)]
    (str sql column-string ")")))

(defn drop-table-contents [name]
  (str "drop table " (-> (string/split name #"-")
                         (last))))

(defn contents [name args]
  (cond
    (string/starts-with? name "create-") {:up (create-table-contents name args)
                                          :down (drop-table-contents name)}
    :else {:up "" :down ""}))

(defn create [name & args]
  (let [migration-file (migration-file-path name)
        _ (.mkdirs (File. migrations-dir))]
    (spit migration-file (-> (contents name args)
                             (pr-str)))
    (println (str "resources/sql/" migration-file " created"))))
