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
         (mapcat identity)
         (into {}))))

(defn validate
  "Validate the map `m` with a vector of rules `validations`.

  For example:
  ```
  (validate {:customer/id 123
             :customer/email \"sean@example.com\"}
            [[:required [:customer/id :customer/email]]
             [:email [:customer/email]]])
  ;; => {:customer/id 123
         :customer/email \"sean@example.com\"}

  (validate {} [[:required [:customer/id] \"can't be blank\"]])
  ;; => Unhandled clojure.lang.ExceptionInfo
  ;;    Invalid data: :customer/id
  ;;    {:type :invalid,
  ;;     :errors #:customer{:id \"Id can't be blank\"},
  ;;     :coast.validation/error :validation,
  ;;     :coast.error/raise true}
  ```

  See [Validator](https://coastonclojure.com/docs/validator.md) for more.
  "
  [m validations]
  (let [errors (-> (v/validate m validations)
                   (fmt-validations))]
    (if (empty? errors)
      m
      (raise (str "Invalid data: " (string/join ", " (keys errors)))
             {:type   :invalid
              :errors errors
              ::error :validation}))))
