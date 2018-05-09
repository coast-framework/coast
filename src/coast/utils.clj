(ns coast.utils
  (:require [clojure.string :as string])
  (:import (java.util UUID)))

(defn uuid
  ([]
   (UUID/randomUUID))
  ([s]
   (UUID/fromString s)))

(defn humanize [k]
  (-> (name k)
      (string/capitalize)
      (string/replace "-" " ")))

(defn parse-int [s]
  (if (string? s)
    (Integer. (re-find  #"^\d+$" s))
    s))

(defn map-vals [f m]
  (->> (map (fn [[k v]] [k (f v)]) m)
       (into {})))

(defn map-keys [f m]
  (->> (map (fn [[k v]] [(f k) v]) m)
       (into {})))

(defn deep-merge [& ms]
  (apply merge-with
         (fn [& vs]
           (if (every? map? vs)
             (apply deep-merge vs)
             (last vs)))
         ms))

(defn convert-keyword [re replacement k]
  (if (keyword? k)
    (let [ns (-> (or (namespace k) "")
                 (string/replace re replacement))
          n (-> (or (name k) "")
                (string/replace re replacement))]
      (if (string/blank? ns)
        (keyword n)
        (keyword ns n)))
    k))

(defn convert-string [re replacement s]
  (if (string? s)
    (string/replace s re replacement)
    s))

(defn convert-case [re replacement val]
  (cond
    (keyword? val) (convert-keyword re replacement val)
    (string? val) (convert-string re replacement val)
    :else val))

(def kebab (partial convert-case #"_" "-"))
(def snake (partial convert-case #"-" "_"))

(defn long-str [& s]
  (let [s (filter some? s)]
    (if (= 1 (count s))
      (first s)
      (string/join "\n" s))))

(defn underline [s]
  (->> (map (fn [_] "-") s)
       (clojure.string/join)))

(def pattern #":([\w-]+):")

(defn replacement [match m]
  (let [default (first match)
        k (-> match last keyword)]
    (str (get m k default))))

(defn fill [m s]
  (string/replace s pattern #(replacement % m)))
