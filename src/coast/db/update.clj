(ns coast.db.update
  (:require [coast.db.schema :as db.schema]
            [coast.time :as time]
            [coast.utils :as utils]
            [clojure.string :as string]))

(defn col [k]
  (if (qualified-ident? k)
    (str (-> k namespace utils/snake) "." (-> k name utils/snake))
    (-> k name utils/snake)))

(defn unqualified-col [k]
  (col (keyword (name k))))

(defn idents [t]
  (let [schema-idents (:idents (db.schema/fetch))]
    (filter #(contains? schema-idents (first %)) t)))

(defn where [t]
  (let [idents (idents t)]
    (if (empty? idents)
      (throw (Exception. "db/transact requires at least one ident"))
      {:where (str "where " (->> idents
                                 (map (fn [[k _]] (str (col k) " = ?")))
                                 (string/join " and ")))})))

(defn cols [t]
  (let [schema-cols (set (conj (:cols (db.schema/fetch)) :updated-at))]
    (filter #(contains? schema-cols (first %)) t)))

(defn table [t]
  (str (->> (map first t)
            (filter qualified-ident?)
            (first)
            (namespace))))

(defn update-set [t]
  (let [cols (cols t)
        table (table t)]
    {:update (str "update " table " set "
                  (->> (map (fn [[k _]] (str (unqualified-col k) " = ?")) cols)
                       (string/join ", ")))}))

(defn sql-map [t]
  (apply merge (update-set t)
               (where t)))

(defn sql-vec [m]
  (let [t (->> (assoc m :updated-at (time/now))
               (map identity))
        cols (cols t)
        idents (idents t)
        {:keys [where update]} (sql-map t)]
    (vec (concat [(string/join " " (filter some? [update where "returning *"]))]
                 (map second cols)
                 (map second idents)))))
