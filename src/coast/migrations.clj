(ns coast.migrations
  (:require [coast.migrations.sql :as migrations.sql]
            [coast.migrations.edn :as migrations.edn]
            [coast.db.connection :refer [connection]]
            [coast.db.queries :as db.queries]
            [coast.db.schema :as schema]
            [coast.db :refer [defq]]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.set :as set])
  (:import (java.io File)))

(defq migrations "sql/migrations.sql")
(defq insert "sql/migrations.sql")
(defq delete "sql/migrations.sql")

(defn migrations-dir []
  (.mkdirs (File. "resources/migrations"))
  "resources/migrations")

(defn migration-files []
  (->> (migrations-dir)
       (io/file)
       (file-seq)
       (filter #(.isFile %))
       (map #(.getName %))
       (filter #(or (.endsWith % ".sql")
                    (.endsWith % ".edn")))))

(defn create-table []
  (jdbc/execute! (connection) (:sql (db.queries/query "create-table" "sql/migrations.sql"))))

(defn completed-migrations []
  (create-table)
  (->> (migrations)
       (map :id)))

(defn pending []
  (let [all (set (migration-files))
        completed (set (completed-migrations))]
    (sort (vec (set/difference all completed)))))


(defn migration-blank? [migration]
  (let [ext (last (re-find #"\.(\w+)$" migration))]
    (condp = ext
      "sql" (string/blank? (migrations.sql/up migration))
      "edn" (empty? (migrations.edn/content migration))
      true)))


(defn migrate []
  (let [migrations (pending)]
    (doseq [migration migrations]
      (let [sql (or (migrations.sql/up migration)
                    (migrations.edn/migrate migration))
            edn (migrations.edn/content migration)
            friendly-name (string/replace migration #"\.edn|\.sql" "")]
        (if (migration-blank? migration)
          (throw (Exception. (format "%s is empty" migration)))
          (do
            (println "")
            (println "-- Migrating: " friendly-name "---------------------")
            (println "")
            (println (or edn sql))
            (println "")
            (println "--" friendly-name "---------------------")
            (println "")
            (jdbc/execute! (connection) sql)
            (insert {:id migration})
            (when (.endsWith migration ".edn")
              (schema/save edn))
            (println friendly-name "migrated successfully")))))))


(defn rollback-blank? [migration]
  (let [ext (last (re-find #"\.(\w+)$" migration))]
    (condp = ext
      "sql" (string/blank? (migrations.sql/down migration))
      "edn" (empty? (migrations.edn/rollback migration))
      true)))


(defn rollback []
  (let [migration (last (completed-migrations))]
    (when (some? migration)
        (let [sql (or (migrations.sql/down migration)
                      (migrations.edn/rollback migration))
              edn (migrations.edn/content migration)
              friendly-name (string/replace migration #"\.edn|\.sql" "")]
          (if (rollback-blank? migration)
            (throw (Exception. (format "%s is empty" migration)))
            (do
              (println "")
              (println "-- Rolling back:" friendly-name "---------------------")
              (println "")
              (println (or edn sql))
              (println "")
              (println "--" friendly-name "---------------------")
              (println "")
              (jdbc/execute! (connection) sql)
              (delete {:id migration})
              (when (.endsWith migration ".edn")
                (schema/save edn))
              (println friendly-name "rolled back successfully")))))))
