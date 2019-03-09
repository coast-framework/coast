(ns coast.db.associations
  (:require [coast.utils :as utils]))


(defn has-many-map [parent m]
  (let [{:keys [has-many table-name foreign-key through primary-key]} m
        table-name (or table-name (utils/singular has-many))
        joins (if (some? through)
                [{:table (utils/singular through)
                  :left (str (utils/singular through) "." parent)
                  :right (str parent "." primary-key)}
                 {:table table-name
                  :left (str table-name ".id")
                  :right (str (utils/singular through) "." table-name)}]
                [{:table table-name
                  :left (str table-name "." (name (or foreign-key parent)))
                  :right (str parent "." primary-key)}])]
    {(keyword parent has-many)
     {:joins joins
      :has-many (keyword (utils/singular has-many))
      :from table-name
      :col (str table-name "." (name (or foreign-key parent)))}}))


(defn belongs-to-map [parent m]
  (let [{:keys [belongs-to foreign-key primary-key]} m]
    {(keyword parent belongs-to)
     {:joins [{:table belongs-to
               :left (str belongs-to "." primary-key)
               :right (str parent "." (or foreign-key belongs-to))}]
      :belongs-to (keyword belongs-to)
      :from belongs-to
      :col (str belongs-to "." primary-key)}}))


(defn primary-key [k]
  {:primary-key (or k "id")})


(defn table [k & args]
  (let [t (name k)
        pk (or (-> (filter #(contains? % :primary-key) args)
                   (first))
               {:primary-key "id"})
        has-many-maps (->> (filter #(contains? % :has-many) args)
                           (map #(merge pk %))
                           (map #(has-many-map t %)))
        belongs-to-maps (->> (filter #(contains? % :belongs-to) args)
                             (map #(merge pk %))
                             (map #(belongs-to-map t %)))]
    (apply merge (concat has-many-maps belongs-to-maps))))


(defn belongs-to [k & {:as m}]
  (->> (select-keys m [:foreign-key])
       (merge {:belongs-to k})
       (utils/map-vals utils/sqlize)))


(defn has-many [k & {:as m}]
  (->> (select-keys m [:table-name :foreign-key :through])
       (merge {:has-many k})
       (utils/map-vals utils/sqlize)))


(defn tables [& args]
  (apply merge args))
