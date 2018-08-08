(ns coast.migrations.edn
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.edn]
            [coast.db.schema :as schema]
            [coast.time :as time])
  (:import (java.io File)))

(defn migrations-dir []
  (.mkdirs (File. "resources/migrations"))
  "resources/migrations")

(defn slurp* [f]
  (when (some? f)
    (slurp f)))

(defn content [migration]
  (when (.endsWith migration ".edn")
    (-> (str "migrations/" migration) io/resource slurp* clojure.edn/read-string)))

(defn migrate [migration]
  (let [content (content migration)
        tables (schema/create-tables-if-not-exists content)
        cols (schema/add-columns content)
        idents (schema/add-idents content)
        rels (schema/add-rels content)
        constraints (schema/add-constraints content)]
    (->> (concat tables cols idents rels constraints)
         (filter some?)
         (string/join ";\n"))))

(defn rollback [migration]
  (let [content (content migration)
        ;TODO tables (schema/drop-table content)
        cols (schema/drop-columns content)
        constraints (schema/drop-constraints content)]
    (->> (concat constraints cols)
         (filter some?)
         (string/join ";\n"))))

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
