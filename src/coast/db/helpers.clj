(ns coast.db.helpers
  (:require [coast.utils :as utils]
            [clojure.string :as string])
  (:refer-clojure :exclude [update]))


(defn insert
  "Builds an insert keyword sql vector from a map or sequence of maps.
   All maps need to have the same keys."
  [val]
  (when (nil? val)
    (throw (Exception. "coast/insert was called with nil")))
  (let [v (utils/vectorize val)
        cols (->> (mapcat identity v)
                  (map first)
                  (distinct))
        unqualified-ks (filter #(not (qualified-ident? %)) cols)
        _ (when (not (empty? unqualified-ks))
            (throw (Exception. "coast/insert requires all maps to have qualified keys. The namespace is used to determine the table to insert into.")))
        distinct-namespaces (distinct
                             (map namespace cols))
        _ (when (not= 1 (count distinct-namespaces))
            (throw (Exception. (str "coast/insert found multiple namespaces. "
                                    "Which table did you want to insert into?"
                                    (string/join ", " distinct-namespaces)))))
        values (map #(map (fn [x] (second x)) %) v)]
    (concat
     (conj cols :insert)
     (conj values :values))))


(defn update
  "Builds a keyword sql vector from a map or sequence of maps.
   All maps need to have the same id column (primary key) value and the same set of keys.
   The values of the maps can and should be different"
  [val]
  (let [v (utils/vectorize val)
        table (-> v first keys first namespace)
        _ (when (nil? table)
            (throw (Exception. "coast/update requires all maps to have qualified keys, could not find table to update")))
        pk (first (filter #(= "id" (name %)) (-> v first keys)))
        pk-values (mapv #(get % pk) v)]
    (if (not= 0 (count
                 (filter nil? pk-values)))
      (throw (Exception. (str "coast/update found no value for the :id key in the following maps: " (vec (filter #(nil? (get % pk)) v)))))
      (concat
        [:update table
         :where [pk pk-values]]
        (conj (->> (mapcat identity v)
                   (distinct))
              :set)))))


(defn upsert
  "Builds an upsert keyword sql vector from a map or sequence of maps.
   All maps need to have the same keys."
  [val opts]
  (when (nil? val)
    (throw (Exception. "coast/upsert was called with nil")))
  (let [v (utils/vectorize val)
        cols (->> (mapcat identity v)
                  (map first)
                  (distinct))
        unqualified-ks (filter #(not (qualified-ident? %)) cols)
        _ (when (not (empty? unqualified-ks))
            (throw (Exception. "coast/upsert requires all maps to have qualified keys. The namespace is used to determine the table to insert into.")))
        distinct-namespaces (distinct
                             (map namespace cols))
        _ (when (not= 1 (count distinct-namespaces))
            (throw (Exception. (str "coast/upsert found multiple namespaces. "
                                    "Which table did you want to insert into?"
                                    (string/join ", " distinct-namespaces)))))
        values (map #(map (fn [x] (second x)) %) v)
        excluded-cols (->> (filter #(not (contains? (set (:on-conflict opts)) (name %))) cols)
                           (map #(vector % (str "excluded." (name %)))))]
    (concat
     (conj cols :insert)
     (conj values :values)
     (conj excluded-cols :do-update-set)
     (conj (:on-conflict opts) :on-conflict))))


(defn delete
  "Builds a keyword sql vector from a map or sequence of maps. All maps need to have one id column specified"
  [val]
  (let [v (utils/vectorize val)
        table (-> v first keys first namespace)
        _ (when (nil? table)
            (throw (Exception. "coast/delete requires all maps to have qualified keys, could not find table to delete from")))
        pk (first (filter #(= "id" (name %)) (-> v first keys)))
        _ (when (nil? pk)
            (throw (Exception. "coast/delete couldn't find the qualified :id key")))]
    [:delete
     :from table
     :where [pk (map #(get % pk) v)]]))
