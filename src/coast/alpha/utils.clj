(ns coast.alpha.utils
  (:require [coast.alpha.env :as env]
            [jkkramer.verily :as v]
            [clojure.string :as string])
  (:import (java.util UUID)
           (clojure.lang ExceptionInfo)))

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

(defn in? [val coll]
  (not= -1 (.indexOf coll val)))

(defn map-vals [f m]
  (->> (map (fn [[k v]] [k (f v)]) m)
       (into {})))

(defn map-keys [f m]
  (->> (map (fn [[k v]] [(f k) v]) m)
       (into {})))

(def test? (= "test" (env/env :coast-env)))
(def prod? (= "prod" (env/env :coast-env)))
(def dev? (= "dev" (env/env :coast-env)))

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
        (keyword ns n)))))

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
  (string/join "\n" s))

(defn flip [f]
  (fn [& args]
    (apply f (reverse args))))

