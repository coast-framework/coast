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

(defn column [arg]
  (let [[name type] (string/split arg #":")]
    (str name " " type)))

(defn column-name [arg]
  (-> arg
      (string/split #":")
      (first)))

(defn parse-table-name [s]
  (->> (string/split s #"[-_]")
       (drop 1)
       (string/join "_")))

(defn create-table-contents [name args]
  (let [table (parse-table-name name)
        sql (str "create table " table " (\n")
        columns (map column args)
        columns (-> columns
                    (conj "id serial primary key")
                    vec
                    (conj "created_at timestamptz default now()"))
        column-string (string/join ",\n  " columns)]
    (str sql column-string "\n)")))

(defn drop-table-contents [s]
  (str "drop table " (parse-table-name s)))

(defn add-column-contents [s args]
  (let [table (->> (re-seq #"add-columns-to-([\w-_]+)" s)
                   (first)
                   (last))
        columns (map column args)]
    (->> (map #(str "alter table " table  " add column " % ";") columns)
         (string/join "\n"))))

(defn add-column-contents-down [s args]
  (let [table (->> (re-seq #"add-columns-to-([\w-_]+)" s)
                   (first)
                   (last))
        columns (map column args)]
    (->> (map #(str "alter table " table  " drop " % ";") columns)
         (string/join "\n"))))

(defn drop-column-contents [s args]
  (let [table (->> (re-seq #"drop-columns-from-([\w-_]+)" s)
                   (first)
                   (last))
        columns (map column-name args)]
    (->> (map #(str "alter table " table  " drop " % ";") columns)
         (string/join "\n"))))

(defn drop-column-contents-down [s args]
  (let [table (->> (re-seq #"drop-columns-from-([\w-_]+)" s)
                   (first)
                   (last))
        columns (map column-name args)]
    (->> (map #(str "alter table " table  " add column " % ";") columns)
         (string/join "\n"))))

(defn contents [s args]
  (cond
    (string/starts-with? s "create-") (str "-- up\n" (create-table-contents s args) "\n\n"
                                           "-- down\n" (drop-table-contents s))
    (string/starts-with? s "drop-") (str "-- up\n\n"
                                         "-- down\n" (drop-table-contents s))
    (string/starts-with? s "add-columns-to-") (str "-- up\n" (add-column-contents s args) "\n\n"
                                                   "-- down\n" (add-column-contents-down s args))
    (string/starts-with? s "drop-columns-from-") (str "-- up\n" (drop-column-contents s args) "\n\n"
                                                      "-- down\n" (drop-column-contents-down s args))
    :else empty-migration))

(defn create [name & args]
  (let [migration (filename name)
        dir (migrations-dir)]
    (spit (str dir "/" migration) (contents name args))
    (println (str dir "/" migration " created"))
    migration))
