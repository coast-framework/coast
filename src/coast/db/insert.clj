(ns coast.db.insert
  (:require [coast.utils :as utils]
            [clojure.string :as string]))

(defn col [k]
  (if (qualified-ident? k)
    (str (-> k namespace utils/snake) "." (-> k name utils/snake))
    (-> k name utils/snake)))

(defn unqualified-col [k]
  (col (keyword (name k))))

(defn table [t]
  (str (->> (map first t)
            (filter qualified-ident?)
            (first)
            (namespace))))

(defn insert-into [t]
  {:insert-into (str "insert into " (table t) " ("
                     (->> (map first t)
                          (map unqualified-col)
                          (string/join ", "))
                    ")")})

(defn values [t]
  {:values (str "values (" (->> (map (fn [_] "?") t)
                                (string/join ","))
                ")")})

(defn sql-map [t]
  (apply merge (insert-into t)
               (values t)))

(defn sql-vec [m]
  (let [tuples (map identity m)
        {:keys [insert-into values]} (sql-map tuples)]
    (vec (concat [(string/join " " (filter some? [insert-into values "returning *"]))]
                 (map second tuples)))))
