(ns coast.migrations.sql
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [coast.time :as time])
  (:import (java.io File)))

(def empty-migration "-- up\n\n-- down")
(def migration-regex #"(?s)--\s*up\s*(.+)--\s*down\s*(.+)")

(defn migrations-dir []
  (.mkdirs (File. "resources/migrations"))
  "resources/migrations")

(defn parse [s]
  (when (string? s)
    (let [[_ up down] (re-matches migration-regex s)]
      {:up up
       :down down})))

(defn slurp* [s]
  (when (some? s)
    (slurp s)))

(defn up [migration]
  (-> (str "migrations/" migration) io/resource slurp* parse :up))

(defn down [migration]
  (-> (str "migrations/" migration) io/resource slurp* parse :down))

(defn timestamp []
  (-> (time/now)
      (time/fmt "yyyyMMddHHmmss")))

(defn filename [name]
  (when (and
          ((comp not nil?) name)
          ((comp not empty?) name)
          (string? name))
    (str (timestamp) "_" (string/replace name #"\s+|-+|_+" "_") ".sql")))

(defn create [name]
  (let [migration (filename name)
        dir (migrations-dir)]
    (spit (str dir "/" migration) "-- up\n\n-- down")
    (println (str dir "/" migration " created"))
    migration))
