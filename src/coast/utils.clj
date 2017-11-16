(ns coast.utils
  (:require [environ.core :as environ]
            [clojure.string :as string])
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

(defn printerr [header body]
  (println "--" header "--------------------")
  (println "")
  (println body))

(def dev? (= "dev" (environ/env :coast-env)))
(def test? (= "test" (environ/env :coast-env)))
(def prod? (= "prod" (environ/env :coast-env)))

(defn current-user [request]
  (get-in request [:session :identity]))

(defn unique-index-error? [error]
  (when (not (nil? error))
    (string/includes? error "duplicate key value violates unique constraint")))
