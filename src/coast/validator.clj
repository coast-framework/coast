(ns coast.validator
  (:require [jkkramer.verily :as v]
            [coast.utils :as utils]
            [clojure.string :as string]
            [error.core :as error]))


(defn nest [k v]
  (let [[table column] (->> (string/split (name k) #"\.")
                            (map keyword))]
    {table {column v}}))


(defn fmt-validation [result]
  (let [{:keys [keys msg]} result]
    (mapv #(nest % (str (->> % name (re-find #"\.(\w+)") last utils/humanize) " " msg)) keys)))


(defn fmt-validations [results]
  (when (some? results)
    (->> (map fmt-validation results)
         (mapcat identity)
         (apply merge-with merge))))


(defn validate [m validations]
  (let [errors (-> (v/validate m validations)
                   (fmt-validations))]
    (if (empty? errors)
      m
      (error/raise errors))))


(defn columns [table columns]
  (fn [params-map]
    (let [filtered-map (select-keys (get params-map table) columns)]
      {table filtered-map})))


(defn nested-keyword [table field]
  (if (keyword? field)
    (keyword (str (name table) "." (name field)))
    field))


(defn param [table validation]
  (let [[type fields msg] validation]
    (if (keyword? (first fields))
      [type (mapv #(nested-keyword table %) fields) msg]
      (vec
       (concat [type] (mapv #(nested-keyword table %) fields) [msg])))))


(defn params [table & validations]
  (fn [params-map]
    (validate params-map (mapv #(param table %) validations))))
