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

(defn create [name]
  (let [migration-file (migration-file-path name)
        _ (.mkdirs (File. migrations-dir))]
    (spit migration-file ragtime-format-edn)
    (println (str "resources/sql/" migration-file " created"))))
