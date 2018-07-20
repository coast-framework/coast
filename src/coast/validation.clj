(ns coast.validation
  (:require [jkkramer.verily :as v]
            [coast.utils :as utils]
            [clojure.string :as string]
            [coast.error :refer [raise]]))

(defn fmt-validation [result]
  (let [{:keys [keys msg]} result]
    (map #(vector % (str (utils/humanize %) " " msg)) keys)))

(defn fmt-validations [results]
  (when (some? results)
    (->> (map fmt-validation results)
         (first)
         (into {}))))

(defn validate [m validations]
  (let [errors (-> (v/validate m validations)
                   (fmt-validations))]
    (if (empty? errors)
      m
      (raise (str "Invalid data: " (string/join ", " (keys errors)))
             {:type :invalid
              :errors errors
              ::error :validation}))))
