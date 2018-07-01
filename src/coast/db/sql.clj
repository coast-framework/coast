(ns coast.db.sql
  (:require [coast.utils :as utils]
            [clojure.string :as string])
  (:refer-clojure :exclude [ident? update]))

(defn ident? [schema val]
  (and
    (vector? val)
    (contains? (:idents schema) (first val))))

(defn same-ns? [m]
  (and (map? m)
       (= 1 (->> m keys (map namespace) (distinct) (count)))))

(defn qualified-map? [m]
  (and (map? m)
       (not (empty? m))
       (every? qualified-keyword? (keys m))))

(defn col [table val]
  (when (not (nil? val))
    (let [prefix (if (nil? table)
                   ""
                   (str table "."))]
      (->> val name utils/snake
           (str prefix)))))

(defn id [schema ident]
  (when (ident? schema ident)
    (let [table (-> ident first namespace)
          sql (str "select id"
                   " from " table
                   " where " (->> ident first (col table)) " = ?"
                   " limit 1")]
      [sql (second ident)])))

(defn insert? [schema v]
  (and (every? map? v)
       (every? same-ns? v)
       (every? qualified-map? v)
       (= (-> v first count) (count (distinct (mapcat keys v))))))

(defn insert-value [m]
  (str "(" (->> (keys m)
                (map (fn [_] (str "?")))
                (string/join ", "))
       ")"))

(defn insert-values [v]
  (string/join ", " (map insert-value v)))

(defn insert [schema v]
  (let [v (if (map? v) [v] v)]
   v (map #(into (sorted-map) %) v)
   (if (insert? schema v)
     (let [table (-> v first keys first namespace)
           sql (str "insert into " table
                    " ("
                    (->> (keys (first v))
                         (map #(col nil %))
                         (string/join ", "))
                    ") values " (insert-values v)
                    " returning *")]
       (apply conj [sql] (mapcat identity (mapv vals v))))
     {})))

(defn update? [schema m ident]
  (and (ident? schema ident)
       (same-ns? m)
       (qualified-map? m)
       (not (nil? ident))))

(defn update [schema m ident]
  (if (update? schema m ident)
    (let [table (-> m keys first namespace)
          sql (str "update " table
                   " set " (->> (map #(str (col nil %) " = ?") (keys m))
                                (string/join ", "))
                   " where " (->> ident first (col nil)) " = ?"
                   " returning *")]
      (apply conj [sql] (concat (vals m) [(second ident)])))
    {}))

(defn delete? [schema ident]
  (ident? schema ident))

(defn delete [schema ident]
  (if (delete? schema ident)
    (let [table (-> ident first namespace)
          sql (str "delete from " table
                   " where " (->> ident first (col table)) " = ?"
                   " returning *")]
      [sql (second ident)])
    {}))

(defn fetch [schema ident]
  (when (ident? schema ident)
    (let [table (-> ident first namespace)
          sql (str "select *"
                   " from " table
                   " where " (->> ident first (col table)) " = ?"
                   " limit 1")]
      [sql (second ident)])))
