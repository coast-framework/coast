(ns coast.db.schema
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [coast.utils :as utils])
  (:import (java.io File))
  (:refer-clojure :exclude [ident?]))

(defn col? [m]
  (contains? m :db/col))

(defn ident? [m]
  (contains? m :db/ident))

(defn rel? [m]
  (contains? m :db/rel))

(defn not-null [m]
  (when (false? (:db/nil? m))
    "not null"))

(defn ident [m]
  (->> [(-> m :db/ident name utils/snake)
        (:db/type m)
        "unique"
        "not null"]
       (filter some?)
       (string/join " ")))

(defn col-default [m]
  (when (contains? m :db/default)
    (str "default " (get m :db/default))))

(defn col [m]
  (->> [(str "\"" (-> m :db/col name utils/snake) "\"")
        (:db/type m)
        (not-null m)
        (col-default m)]
       (filter some?)
       (string/join " ")))

(defn rel [m]
  (let [table (-> m :db/joins name utils/snake)
        rel-type (-> m :db/type)]
    (if (= :many rel-type)
      (str table "_id integer not null references " table "(id) on delete cascade")
      nil)))

(defn add-column [m]
  (string/join " " ["alter table" (-> m :db/col namespace utils/snake) "add column" (col m)]))

(defn add-columns [v]
  (->> (filter col? v)
       (map add-column)))

(defn add-ident [m]
  (string/join " " ["alter table" (-> m :db/ident namespace utils/snake) "add column" (ident m)]))

(defn add-idents [v]
  (->> (filter ident? v)
       (map add-ident)))

(defn add-rel [m]
  (string/join " " ["alter table" (-> m :db/joins namespace utils/snake) "add column" (rel m)]))

(defn add-rels [v]
  (->> (filter rel? v)
       (map add-rel)))

(defn create-table-if-not-exists [table]
  (str "create table if not exists " table " ("
       " id serial primary key,"
       " updated_at timestamptz,"
       " created_at timestamptz not null default now()"
       " )"))

(defn create-tables-if-not-exists [v]
  (let [idents (->> (filter ident? v)
                    (map :db/ident)
                    (map namespace))
        cols (->> (filter col? v)
                  (map :db/col)
                  (map namespace))
        tables (->> (concat idents cols)
                    (distinct)
                    (map utils/snake))]
    (->> tables
         (map create-table-if-not-exists))))

(defn slurp* [filename]
  (when (.exists (io/file filename))
    (slurp filename)))

(defn filter-schema-by-key [k schema]
  (->> (filter #(contains? % k) schema)
       (map k)))

(def rels (partial filter-schema-by-key :db/rel))
(def cols (partial filter-schema-by-key :db/col))
(def idents (partial filter-schema-by-key :db/ident))

(defn pprint-write [filename val]
  (with-open [w (io/writer filename)]
    (binding [*out* w]
      (pprint/write val))))

(defn join-col [k]
  (let [namespace (-> k namespace utils/snake)
        name (-> k name utils/snake)]
    (str namespace "." name "_id")))

(defn join-statement [k]
  (str "join "
       (-> k namespace utils/snake)
       " on "
       (join-col k)
       " = "
       (str (-> k name utils/snake) ".id")))

(defn joins [schema]
  (let [many-to-one (->> (map #(vector % (join-statement (get-in schema [% :db/joins]))) (:rels schema))
                         (into {}))
        one-to-many (->> (map #(get-in schema [% :db/joins]) (:rels schema))
                         (map #(vector % (keyword (namespace %) (str (name %) "-id"))))
                         (into {}))]
    (merge many-to-one one-to-many)))

(defn fetch []
  (let [resource (io/resource "schema.edn")]
    (if (nil? resource)
      {}
      (-> resource slurp edn/read-string))))

(defn save
  "This saves a schema.edn file for easier reading when it comes time to query the db"
  [schema]
  (let [current-schema (fetch)
        current-rels (:rels current-schema)
        current-cols (:cols current-schema)
        current-idents (:idents current-schema)
        new-schema (reduce (fn [acc m]
                             (assoc acc (or (:db/ident m)
                                            (:db/rel m)
                                            (:db/col m))
                                        (select-keys m [:db/type :db/nil? :db/joins :db/default])))
                           {}
                           schema)
        new-implicit-idents (->> (reduce (fn [acc m]
                                           (conj acc (or (:db/ident m)
                                                         (:db/rel m)
                                                         (:db/col m))))
                                         []
                                         schema)
                                 (map namespace)
                                 (distinct)
                                 (map #(keyword % "id")))
        new-rels (rels schema)
        new-cols (cols schema)
        new-idents (idents schema)
        all-rels (-> (concat current-rels new-rels) (distinct))
        all-cols (-> (concat current-cols new-cols) (distinct))
        all-idents (-> (concat current-idents new-idents new-implicit-idents) (distinct))
        schema-map (-> (merge current-schema new-schema)
                       (assoc :rels (set all-rels)
                              :cols (set all-cols)
                              :idents (set all-idents)))
        schema-map (assoc schema-map :joins (joins schema-map))]
    (pprint-write "resources/schema.edn" schema-map)))
