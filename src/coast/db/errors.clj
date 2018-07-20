(ns coast.db.errors
  (:require [coast.utils :as utils]
            [clojure.string :as string]))

(defn not-null-constraint [s]
  (let [col (-> (re-find #"null value in column \"(.*)\" violates not-null constraint" s)
                (second))]
    (if (nil? col)
      {}
      {(keyword col) (str (utils/humanize col) " cannot be blank")
       ::error :not-null
       :db.constraints/not-null (keyword col)})))

(defn unique-constraint [s]
  (let [col (-> (re-find #"(?s)duplicate key value violates unique constraint.*Detail: Key \((.*)\)=\((.*)\)" s)
                (second))]
    (if (nil? col)
      {}
      {(keyword col) (str (utils/humanize col) " is already taken")
       ::error :unique-contraint
       :db.constraints/unique (keyword col)})))

(defn error-map [ex]
  (let [s (.getMessage ex)
        m1 (not-null-constraint s)
        m2 (unique-constraint s)]
    (merge m1 m2)))
