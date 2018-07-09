(ns coast.migrations.edn
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.edn]
            [clojure.java.jdbc :as jdbc]
            [coast.db :refer [defq] :as db]
            [coast.db.queries :as queries]
            [coast.db.connection :refer [connection]]
            [coast.db.schema :as schema]
            [coast.time :as time])
  (:import (java.io File))
  (:refer-clojure :exclude [read]))

(defn migrations-dir []
  (.mkdirs (File. "resources/migrations"))
  "resources/migrations")

(defq migrations "sql/migrations.sql")
(defq insert "sql/migrations.sql")
(defq delete "sql/migrations.sql")

(defn create-table []
  (->> (queries/query "create-table" "sql/migrations.sql")
       :sql
       (jdbc/execute! (connection))))

(defn completed-migrations []
  (let [_ (create-table)]
    (->> (migrations)
         (map :id))))

(defn migration-files []
  (->> (migrations-dir)
       (io/file)
       (file-seq)
       (filter #(.isFile %))
       (map #(.getName %))
       (filter #(.endsWith % ".edn"))))

(defn pending []
  (let [all (set (migration-files))
        completed (set (completed-migrations))]
    (sort (vec (set/difference all completed)))))

(defn path [s]
  (format "%s/%s" (migrations-dir) s))

(defn read [s]
  (-> s path slurp))

(defn parse [s]
  (when (string? s)
    (clojure.edn/read-string s)))

(defn migrate-schema [conn schema]
  (let [create-statements (schema/create-tables-if-not-exists schema)
        col-statements (schema/add-columns schema)
        ident-statements (schema/add-idents schema)
        rel-statements (schema/add-rels schema)
        _ (doall
            (for [s create-statements]
              (jdbc/execute! conn s)))
        _ (doall
            (for [s ident-statements]
              (jdbc/execute! conn s)))
        _ (doall
            (for [s col-statements]
              (jdbc/execute! conn s)))
        _ (doall
            (for [s rel-statements]
              (jdbc/execute! conn s)))]
      (schema/save schema)))

(defn migrate []
  (let [migrations (pending)]
    (doseq [migration migrations]
      (let [contents (-> migration read parse)
            friendly-name (string/replace migration #"\.edn" "")]
        (if (or (empty? contents))
          (throw (Exception. (format "%s is empty" migration)))
          (do
            (println "")
            (println "-- Migrating: " friendly-name "---------------------")
            (println "")
            (println contents)
            (println "")
            (println "--" friendly-name "---------------------")
            (println "")
            (migrate-schema (connection) contents)
            (insert {:id migration})
            (println friendly-name "migrated successfully")))))))

(defn timestamp []
  (-> (time/now)
      (time/fmt "yyyyMMddHHmmss")))

(defn filename [name]
  (when (and
          ((comp not nil?) name)
          ((comp not empty?) name)
          (string? name))
    (str (timestamp) "_" (string/replace name #"\s+|-+|_+" "_") ".edn")))

(defn create [name & args]
  (let [migration (filename name)
        dir (migrations-dir)]
    (spit (str dir "/" migration) "[]")
    (println (str dir "/" migration " created"))
    migration))
