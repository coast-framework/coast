(ns coast.utils
  (:require [environ.core :as environ]
            [clojure.string :as string]
            [jkkramer.verily :as v])
  (:import (java.time LocalDateTime)
           (java.util UUID)
           (clojure.lang ExceptionInfo)))

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
      [nil (.getMessage e#)])))

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

(defn throw+ [m]
  (if (map? m)
    (throw (ex-info "App exception" (merge m {:coast/exception true})))
    (throw (Exception. "Throw+ only accepts maps, not strings"))))

(defmacro try+ [fn]
  `(try
     [~fn nil]
     (catch ExceptionInfo e#
       (if (true? (get (ex-data e#) :coast/exception))
         [nil (dissoc (ex-data e#) :coast/exception)]
         (throw e#)))))

(defn humanize [k]
  (-> (name k)
      (string/capitalize)
      (string/replace "-" " ")))

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
      (throw+ (merge result {:coast/error "Validation has failed"})))))
