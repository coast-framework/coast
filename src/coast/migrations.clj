(ns coast.migrations
  (:require [coast.migrations.sql :as migrations.sql]
            [coast.migrations.edn :as migrations.edn]
            [coast.db.migrations]
            [coast.db.connection :refer [connection]]
            [coast.db.queries :as db.queries]
            [coast.db.schema :as schema]
            [coast.db]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.set :as set])
  (:import (java.io File))
  (:refer-clojure :exclude [boolean]))


(defn migrations-dir []
  (.mkdirs (File. "db/migrations"))
  "db/migrations")


(defn migration-files []
  (->> (migrations-dir)
       (io/file)
       (file-seq)
       (filter #(.isFile %))
       (map #(.getName %))
       (filter #(or (.endsWith % ".sql")
                    (.endsWith % ".edn")
                    (.endsWith % ".clj")))))


(defn create-table []
  (jdbc/db-do-commands (connection)
    (jdbc/create-table-ddl :coast_schema_migrations
                           [[:version "text" :primary :key]]
                           {:conditional? true})))


(defn completed-migrations []
  (create-table)
  (->> (coast.db/q '[:select coast-schema-migrations/version
                     :order coast-schema-migrations/version])
       (map :coast-schema-migrations/version)))


(defn version [filename]
  (first (string/split filename #"_")))


(defn migration-filename [version]
  (let [filenames (migration-files)]
    (first
     (filter #(string/starts-with? % (str version)) filenames))))


(defn pending []
  (let [filenames (migration-files)
        all (set (map version filenames))
        completed (set (completed-migrations))
        versions (sort (set/difference all completed))]
    (map migration-filename versions)))


(defn statements [filename]
  (let [migration-type (last
                        (string/split filename #"\."))
        filename-with-path (str (migrations-dir) "/" filename)
        contents (slurp filename-with-path)]
    (condp = migration-type
      "sql" {:sql (migrations.sql/up contents)
             :filename filename}
      "edn" {:sql (migrations.edn/migrate contents)
             :raw contents
             :filename filename}
      (let [f (load-file filename-with-path)
            output (f)]
        {:sql (string/join "; " output)
         :vec output
         :filename filename}))))


(defn migrate []
  (let [migrations (pending)]
    (doseq [migration migrations]
      (let [statements (statements migration)
            friendly-name (string/replace migration #"\.edn|\.sql|\.clj" "")]
        (if (string/blank? (:sql statements))
          (throw (Exception. (format "%s is empty" migration)))
          (do
            (println "")
            (println "-- Migrating: " friendly-name "---------------------")
            (println "")
            (println (or (:raw statements) (:sql statements)))
            (println "")
            (println "--" friendly-name "---------------------")
            (println "")
            (jdbc/db-do-commands (connection) (or (:vec statements) (:sql statements)))
            (jdbc/insert! (connection) :coast_schema_migrations {:version (version migration)})
            (when (.endsWith migration ".edn")
              (schema/save (:raw statements)))
            (println friendly-name "migrated successfully")))))))


(defn rollback-statements [filename]
  (let [migration-type (last
                        (string/split filename #"\."))
        filename-with-path (str (migrations-dir) "/" filename)
        contents (slurp filename-with-path)]
    (condp = migration-type
      "sql" {:sql (migrations.sql/down contents)
             :filename filename}
      "edn" {:sql (migrations.edn/rollback contents)
             :raw contents
             :filename filename}
      (let [f (load-file filename-with-path)
            output (f)]
        {:sql (string/join ";" output)
         :vec output
         :filename filename}))))


(defn rollback []
  (let [migration (migration-filename (last (completed-migrations)))
        _ (reset! coast.db.migrations/rollback? true)]
    (when (some? migration)
        (let [statements (rollback-statements migration)
              friendly-name (string/replace migration #"\.edn|\.sql|\.clj" "")]
          (if (string/blank? (:sql statements))
            (throw (Exception. (format "%s is empty" migration)))
            (do
              (println "")
              (println "-- Rolling back:" friendly-name "---------------------")
              (println "")
              (println (or (:raw statements) (:sql statements)))
              (println "")
              (println "--" friendly-name "---------------------")
              (println "")
              (jdbc/db-do-commands (connection) (or (:vec statements) (:sql statements)))
              (jdbc/delete! (connection) :coast_schema_migrations ["version = ?" (version migration)])
              (when (.endsWith migration ".edn")
                (schema/save (:raw statements)))
              (println friendly-name "rolled back successfully")))))))


(defn -main [action]
  (case action
    "migrate" (migrate)
    "rollback" (rollback)
    ""))
