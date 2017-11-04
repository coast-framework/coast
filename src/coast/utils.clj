(ns coast.utils
  (:require [environ.core :as environ])
  (:import (java.util UUID Date)))

(defn uuid
  ([]
   (UUID/randomUUID))
  ([s]
   (UUID/fromString s)))

(defn now []
  (new Date))

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

(defn coerce-int? [s]
  (and
    (string? s)
    (not (nil? (re-find #"^\d+$" s)))))

(defn coerce-uuid? [s]
  (and
    (string? s)
    (not (nil? (re-find #"(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$" s)))))

(defn coerce-string [s]
  (cond
    (coerce-int? s) (parse-int s)
    (coerce-uuid? s) (uuid s)
    :else s))

(defn printerr [header body]
  (println "--" header "--------------------")
  (println "")
  (println body))

(def dev? (= "dev" (environ/env :coast-env)))
(def test? (= "test" (environ/env :coast-env)))
(def prod? (= "prod" (environ/env :coast-env)))
