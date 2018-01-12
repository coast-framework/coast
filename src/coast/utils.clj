(ns coast.utils
  (:require [environ.core :as environ]
            [clojure.string :as string]
            [jkkramer.verily :as v])
  (:import (java.time LocalDateTime)
           (java.util UUID)))

(defn uuid
  ([]
   (UUID/randomUUID))
  ([s]
   (UUID/fromString s)))

(defn now []
  (LocalDateTime/now))

(defn humanize [k]
  (-> (name k)
      (string/capitalize)
      (string/replace "-" " ")))

(defmacro try! [fn]
  `(try
    [~fn nil]
    (catch Exception e#
      [nil (or (ex-data e#) (.getMessage e#))])))

(defn parse-int [s]
  (if (string? s)
    (Integer. (re-find  #"^\d+$" s))
    s))

(defn map-vals [f m]
  (->> m
       (map (fn [[k v]] [k (f v)]))
       (into {})))

(defn printerr [header body]
  (println "-- " header " --------------------")
  (println "")
  (println body))

(def test? (= "test" (environ/env :coast-env)))
(def prod? (= "prod" (environ/env :coast-env)))
(def dev? (not prod?))

(defn current-user [request]
  (get-in request [:session :identity]))

(defn fmt-validation [result]
  (let [{:keys [keys msg]} result]
    (map #(hash-map % (str (humanize %) " " msg)) keys)))

(defn fmt-validations [results]
  (when (some? results)
    (->> (map fmt-validation results)
         (flatten)
         (into {}))))

(defn validate [m validations]
  (let [result (-> (v/validate m validations)
                   (fmt-validations))]
    (if (empty? result)
      m
      (throw (ex-info "Validation has failed" result)))))
