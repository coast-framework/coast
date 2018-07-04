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
       (string/join " ")))

(defn col [m]
  (->> [(-> m :db/col name utils/snake)
        (:db/type m)
        (not-null m)
        (:db/default m)]
       (string/join " ")))

(defn rel [m]
  (let [table (-> m :db/joins name utils/snake)
        rel-type (-> m :db/type)]
    (if (= :many rel-type)
      (str table "_id integer not null references " table "(id) on delete cascade")
      nil)))

(defn add-column [[table cols]]
  (let [col-str (->> (map #(str " add column " (col %)) cols)
                     (string/join ", "))]
    (str "alter table " table " " col-str)))

(defn add-columns [v]
  (->> (filter col? v)
       (group-by #(-> % :db/col namespace))
       (map add-column)))

(defn add-ident [[table cols]]
  (let [ident-str (->> (map #(str " add column " (ident %)) cols)
                       (string/join ", "))]
    (str "alter table " table " " ident-str)))

(defn add-idents [v]
  (->> (filter ident? v)
       (group-by #(-> % :db/ident namespace))
       (map add-ident)))

(defn add-rel [[table rels]]
  (str "alter table " table " " (->> (map #(str " add column " (rel %)) rels)
                                     (string/join ", "))))

(defn add-rels [v]
  (->> (filter rel? v)
       (group-by #(-> % :db/joins namespace))
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

(defn save
  "This saves a schema.edn file for easier reading when it comes time to query the db"
  [schema]
  (let [current-schema (or (-> "resources/schema.edn" slurp* edn/read-string)
                           {})
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
                              :idents (set all-idents)))]
    (pprint-write "resources/schema.edn" schema-map)))

(defn fetch []
  (or (-> "resources/schema.edn" slurp* edn/read-string)
      {}))
