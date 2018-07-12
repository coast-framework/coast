(ns coast.validator
  (:require [jkkramer.verily :as v]
            [coast.utils :as utils]
            [clojure.string :as string]))

(defn fmt-validation [result]
  (let [{:keys [keys msg]} result]
    (map #(hash-map % (str (utils/humanize %) " " msg)) keys)))

(defn fmt-validations [results]
  (when (some? results)
    (->> (map fmt-validation results)
         (flatten)
         (into {}))))

(defn validate [validations m]
  (let [errors (-> (v/validate m validations)
                   (fmt-validations))]
    (if (empty? errors)
      m
      (throw
       (ex-info (str "Invalid data: " (string/join ", " (keys errors)))
                {:type :invalid
                 :errors errors})))))
