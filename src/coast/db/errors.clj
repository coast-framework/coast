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
  (let [[name cs vs] (->> (re-seq #"(?s)duplicate key value violates unique constraint \"(.*)\".*Detail: Key \((.*)\)=\((.*)\)" s)
                          (first)
                          (drop 1))
        table (first (string/split name #"_"))
        cols (->> (string/split cs #",")
                  (map string/trim)
                  (map keyword))
        msg-values (map #(str (utils/humanize %) " already exists") cols)
        values (->> (string/split vs #",")
                    (map string/trim))
        m (zipmap cols values)
        message-map (zipmap cols msg-values)]
    (merge message-map {:db.constraints/unique name
                        ::error :unique-constraint
                        ::value m
                        ::message (str "That " table " already exists")})))

(defn error-map [ex]
  (let [s (.getMessage ex)
        m1 (not-null-constraint s)
        m2 (unique-constraint s)]
    (merge m1 m2)))
