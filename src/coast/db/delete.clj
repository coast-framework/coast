(ns coast.db.delete
  (:require [coast.utils :as utils]
            [clojure.string :as string]))

(defn col
  ([table val]
   (when (ident? val)
     (let [prefix (if (nil? table)
                    ""
                    (str table "."))]
       (->> val name utils/snake
            (str prefix))))))

(defn same-ns? [m]
  (and (map? m)
       (= 1 (->> m keys (map namespace) (distinct) (count)))))

(defn qualified-map? [m]
  (and (map? m)
       (not (empty? m))
       (every? qualified-ident? (keys m))))

(defn validate-transaction [m]
  (cond
    (not (same-ns? m)) (throw (Exception. "All keys must have the same namespace"))
    (not (qualified-map? m)) (throw (Exception. "All keys must be qualified"))
    :else m))

(defn ? [m]
  (->> (keys m)
       (map (fn [_] (str "?")))
       (string/join ", ")))

(defn sql-vec [val]
  (let [v (if (sequential? val) val [val])
        v (map validate-transaction v)
        table (-> v first keys first namespace utils/snake)
        sql (str "delete from " table
                 " where " (->> v first keys first (col table)) " in "
                 "(" (->> (map ? v)
                          (mapcat identity)
                          (string/join ", "))
                 ")"
                 " returning *")]
    (vec (apply concat [sql] (map #(-> % vals) v)))))
