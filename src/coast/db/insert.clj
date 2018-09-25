(ns coast.db.insert
  (:require [coast.utils :as utils]
            [clojure.string :as string]))

(defn col [k]
  (if (qualified-ident? k)
    (str (-> k namespace utils/snake) "." (-> k name utils/snake))
    (-> k name utils/snake)))

(defn unqualified-col [k]
  (-> k name utils/snake))

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
  {:values (str "values " (string/join ","
                           (map #(str "(" (->> (map (fn [_] "?") %)
                                               (string/join ","))
                                     ")")
                                t)))})

(defn sql-map [t]
  (apply merge (insert-into (first t))
               (values t)))

(defn tuple [m]
  (mapv identity m))

(defn sql-vec [arg]
  (let [v (if (sequential? arg) arg [arg])
        tuples (mapv tuple v)
        {:keys [insert-into values]} (sql-map tuples)]
    (vec (concat [(string/join " " (filter some? [insert-into values "returning *"]))]
                 (mapcat #(map second %) tuples)))))
