(ns coast.migrations.sql
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [coast.time :as time])
  (:import (java.io File)))

(def migration-regex #"(?s)--\s*up\s*(.+)--\s*down\s*(.+)")

(defn parse [s]
  (when (string? s)
    (let [[_ up down] (re-matches migration-regex s)]
      {:up up
       :down down})))

(defn up [contents]
  (-> contents parse :up))

(defn down [contents]
  (-> contents parse :down))
