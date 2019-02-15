(ns coast.db.associations
  (:require [coast.utils :as utils]))


(defn has-many-map [table m]
  (let [joins (if (some? (:through m))
                [{:join/table (utils/singular (:through m))
                  :join/left (keyword (utils/singular (:through m)) table)
                  :join/right (keyword table (or (:pk m) "id"))}
                 {:join/table (keyword (:table-name m))
                  :join/left (keyword (:table-name m) "id")
                  :join/right (keyword (utils/singular (:through m)) (:table-name m))}]
                [{:join/table (keyword (:table-name m))
                  :join/left (keyword (:table-name m) (name (or (:foreign-key m) table)))
                  :join/right (keyword table (or (:pk m) "id"))}])]
    {(keyword table (:rel-name m)) {:joins joins
                                    :has-many true}}))


(defn belongs-to-map [table m]
  {(keyword table (:rel-name m))
   {:joins [{:join/table (keyword (:table-name m))
             :join/left (keyword (:table-name m) (name (or (:pk m) table)))
             :join/right (keyword table (or (:foreign-key m) "id"))}]
    :belongs-to true}})


(defn primary-key [k]
  (if (ident? k)
    {:pk (name k)}
    {:pk k}))


(defn table [k & args]
  (let [t (name k)
        pk (first
            (filter #(contains? % :pk) args))
        has-many-maps (->> (map :has-many args)
                           (filter some?)
                           (map #(merge pk %))
                           (map #(has-many-map t %)))
        belongs-to-maps (->> (map :belongs-to args)
                             (filter some?)
                             (map #(merge pk %))
                             (map #(belongs-to-map t %)))]
    (apply merge (concat has-many-maps belongs-to-maps))))


(defn belongs-to [k & {:as m}]
  (let [rel-name (name k)
        table-name (or (:table-name m) (utils/singular rel-name))
        foreign-key (:foreign-key m)]
    (merge
      (primary-key (:primary-key m))
      {:belongs-to {:rel-name rel-name
                    :foreign-key foreign-key
                    :table-name table-name}})))


(defn has-many [k & {:as m}]
  (let [rel-name (name k)
        table-name (or (:table-name m) (utils/singular rel-name))
        foreign-key (:foreign-key m)
        through (when (some? (:through m))
                  (name (:through m)))]
    {:has-many {:rel-name rel-name
                :foreign-key foreign-key
                :table-name table-name
                :through through}}))


(defn tables [& args]
  (apply merge args))
