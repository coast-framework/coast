(ns coast.db.schema
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [coast.utils :as utils])
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
  (->> [(-> (:db/ident m) name utils/snake)
        (:db/type m)
        "unique"
        "not null"]
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

(defn add-column [m]
  (str "alter table " (-> m :db/col namespace utils/snake) " add column " (col m)))

(defn add-columns [v]
  (->> (filter col? v)
       (map add-column)))

(defn add-ident [m]
  (let [table (-> m :db/ident namespace utils/snake)]
    (str "alter table " table " add column " (ident m))))

(defn add-idents [v]
  (->> (filter ident? v)
       (map add-ident)))

(defn name* [ident]
  (when (clojure.core/ident? ident)
    (name ident)))

(defn rel [m]
  (let [ref (condp = (:db/type m)
              :many (-> m :db/joins name* utils/snake)
              :one (-> m :db/rel name* utils/snake)
              nil)
        on-delete (str "on delete " (or (:db/delete m) "cascade"))]
    (when (string? ref)
      (str ref " integer not null references " ref "(id) " on-delete))))

(defn namespace* [ident]
  (when (clojure.core/ident? ident)
    (namespace ident)))

(defn add-rel [m]
  (let [table (condp = (:db/type m)
                :many (-> m :db/joins namespace* utils/snake)
                :one (-> m :db/rel namespace* utils/snake)
                nil)]
    (when (string? table)
      (str "alter table " table " add column " (rel m)))))

(defn add-rels [v]
  (->> (filter rel? v)
       (map add-rel)))

(defn constraint? [m]
  (contains? m :db/constraint))

(defn add-constraint [{:keys [db/constraint db/type]}]
  ; TODO add support for more constraint types (check, foreign keys, delete)?
  (let [table (-> constraint first namespace utils/snake)
        cols (map name constraint)]
    (str "alter table " table " add " type " ("
           (->> (map utils/snake cols)
                (string/join ", "))
           ")")))

(defn add-constraints [v]
  (->> (filter constraint? v)
       (map add-constraint)))

(defn create-table-if-not-exists [table]
  (if (= "user" table)
    (throw (Exception. "user is a reserved word in postgres try a different name for this table"))
    (str "create table if not exists " table " ("
         " id serial primary key,"
         " updated_at timestamptz,"
         " created_at timestamptz not null default now()"
         " )")))

(defn create-tables-if-not-exists [v]
  (let [idents (->> (filter ident? v)
                    (map :db/ident)
                    (filter qualified-ident?)
                    (map namespace))
        one-rels (->> (filter rel? v)
                      (filter #(= (:db/type %) :one))
                      (map :db/rel)
                      (map namespace))
        cols (->> (filter col? v)
                  (map :db/col)
                  (map namespace))
        tables (->> (concat idents cols one-rels)
                    (distinct)
                    (map utils/snake))]
    (map create-table-if-not-exists tables)))

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
    (str namespace "." name)))

(defn join-statement [k]
  (when (qualified-ident? k)
    (str "join "
         (-> k namespace utils/snake)
         " on "
         (join-col k)
         " = "
         (str (-> k name utils/snake) ".id"))))

(defn joins [schema]
  (let [many (->> (map #(vector % (join-statement (get-in schema [% :db/joins]))) (:rels schema))
                  (filter #(some? (second %)))
                  (into {}))
        joins (->> (map #(get-in schema [% :db/joins]) (:rels schema))
                   (filter some?)
                   (map #(vector % (keyword (namespace %) (name %))))
                   (into {}))
        one (->> (map #(get-in schema [% :db/ref]) (:rels schema))
                 (filter some?)
                 (map #(vector % (keyword (namespace %) (name %))))
                 (into {}))]
    (merge many joins one)))

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
        current-constraints (:constraints current-schema)
        new-schema (reduce (fn [acc m]
                             (assoc acc (or (:db/ident m)
                                            (:db/rel m)
                                            (:db/col m)
                                            (:db/constraint m))
                                        (select-keys m [:db/type :db/nil? :db/joins :db/default :db/ref :db/delete])))
                           {}
                           schema)
        new-implicit-idents (->> (reduce (fn [acc m]
                                           (conj acc (or (:db/ident m)
                                                         (:db/rel m)
                                                         (:db/col m)
                                                         (:db/constraint m))))
                                         []
                                         schema)
                                 (map #(or (namespace* %) (-> % first namespace*)))
                                 (distinct)
                                 (map #(keyword % "id")))
        new-rels (rels schema)
        new-cols (cols schema)
        new-idents (idents schema)
        new-constraints (filter constraint? schema)
        all-rels (-> (concat current-rels new-rels) (distinct))
        all-cols (-> (concat current-cols new-cols) (distinct))
        all-idents (-> (concat current-idents new-idents new-implicit-idents) (distinct))
        all-constraints (-> (concat current-constraints new-constraints) (distinct))
        schema-map (-> (merge current-schema new-schema)
                       (assoc :rels (set all-rels)
                              :cols (set all-cols)
                              :idents (set all-idents)
                              :constraints (set all-constraints)))
        schema-map (assoc schema-map :joins (joins schema-map))]
    (pprint-write "resources/schema.edn" schema-map)))

(defn drop-column [m]
  (let [k (or (:db/col m) (:db/ident m) (:db/joins m) (:db/rel m))
        table (-> k namespace utils/snake)
        col (-> k name utils/snake)]
    (str "alter table " table " drop column " col)))

(defn drop-columns [schema]
  (->> (filter #(or (contains? % :db/col)
                    (contains? % :db/ident)
                    (and (contains? % :db/rel)
                         (= :one (:db/type %)))
                    (contains? % :db/joins))
               schema)
       (map drop-column)))

(defn drop-constraint [{:keys [db/constraint]}]
  (let [table (-> constraint first namespace utils/snake)]
    (str "alter table " table "drop constraint " (str table "_" (->> (map #(-> % name utils/snake) constraint)
                                                                     (string/join "_"))
                                                      "_key"))))

(defn drop-constraints [schema]
  (->> (filter #(contains? % :db/constraint) schema)
       (map drop-constraint)))
