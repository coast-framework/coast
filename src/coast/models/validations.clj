(ns coast.models.validations
  (:require [jkkramer.verily :as v]
            [coast.utils :as utils]))

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
       (ex-info "Invalid data" {:type :invalid
                                :errors errors})))))
