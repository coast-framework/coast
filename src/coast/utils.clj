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
  (when (string? s)
    (Integer. (re-find  #"\d+" s))))

(def dev? (= "dev" (environ/env :coast-env)))
(def test? (= "test" (environ/env :coast-env)))
(def prod? (= "prod" (environ/env :coast-env)))
