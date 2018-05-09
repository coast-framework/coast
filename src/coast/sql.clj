(ns coast.sql
  (:require [coast.utils :as utils]
            [clojure.string :as string])
  (:refer-clojure :exclude [find]))

(defn col [table val]
  (when (not (nil? val))
    (let [prefix (if (nil? table)
                   ""
                   (str table "."))]
      (->> val name utils/snake
           (str prefix)))))

(defn select [table v]
  (if (empty? v)
    "select *"
    (->> (map #(col table %) v)
         (string/join ", ")
         (str "select "))))

(defn from [table]
  (str "from " table))

(defn where-part [[k v]]
  (if (nil? v)
    (str k " is null")
    (str k " = ?")))

(defn where [table m]
  (when (map? m)
    (->> (map (fn [[k v]] [(col table k) v]) m)
         (map where-part)
         (string/join " and ")
         (str "where "))))

(defn order [table v]
  (when (vector? v)
    (->> (map #(if (contains? #{:asc :desc} %)
                (name %)
                (col table %)) v)
         (partition-all 2)
         (map (fn [[col dir]] (string/join " " [col dir])))
         (map string/trim)
         (string/join ", ")
         (str "order by "))))

(defn limit [n]
  (when (integer? n)
    (str "limit " n)))

(defn offset [n]
  (when (integer? n)
    (str "offset " n)))

(defn find-by [table m]
  (string/join "\n" [(select table nil)
                     (from table)
                     (where table m)
                     "limit 1"]))

(defn find [table val]
  (string/join "\n" [(select table nil)
                     (from table)
                     (where table {:id val})
                     "limit 1"]))

(defn values [m]
  (->> (vals m)
       (filter some?)))

(defn v [s m]
  (apply conj [s] (values m)))

(defn query [table m]
   (->> [(select table (:select m))
         (from table)
         (where table (:where m))
         (order table (:order m))
         (limit (:limit m))
         (offset (:offset m))]
        (filter #(not (string/blank? %)))
        (string/join "\n")))
