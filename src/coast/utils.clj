(ns coast.utils
  (:require [clojure.string :as string])
  (:import (java.util UUID Base64)
           (java.security SecureRandom)))


(defn uuid
  "Return a random UUID."
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
    (let [val (re-find  #"^\d+$" s)]
      (when (some? val)
        (Integer. val)))
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

(def kebab-case (partial convert-case #"_" "-"))
(def snake-case (partial convert-case #"-" "_"))

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

(defmacro try+ [f error-fn]
  `(try
     ~f
    (catch clojure.lang.ExceptionInfo e#
      (let [ex# (ex-data e#)]
        (~error-fn (ex-data e#))))))

(defn api-route? [handler] handler)

(defn keyword->symbol [k]
  (when (keyword? k)
    (symbol (namespace k) (name k))))

(defn resolve-safely [sym]
  (when (symbol? sym)
    (resolve sym)))

(defn sqlize [val]
  (cond
    (qualified-ident? val) (str (-> val namespace snake-case) "." (-> val name snake-case))
    (ident? val) (-> val name snake-case)
    (string? val) (snake-case val)
    (nil? val) val
    :else (throw (Exception. (str val " is not an ident or a string. Example: :customer, :public/customer or \"customer\"")))))

(defn vectorize [val]
  (if (sequential? val)
    val
    [val]))


(defn surround [ws s]
  (if (string/blank? s)
    ""
    (str (first ws) s (second ws))))


(defn flat [coll]
  (mapcat #(if (sequential? %) % [%]) coll))


(defn sql-vec? [v]
  (and (vector? v)
       (string? (first v))
       (not (string/blank? (first v)))))


(def singular-patterns
  [[#"(?i)ies$" "y"]
   [#"(?i)(\w)\1(es)$" "$1"]
   [#"(?i)(tch)(es)$" "$1"]
   [#"(?i)(ss)$" "$1"]
   [#"(?i)s$" ""]])


(defn replace-pattern [s pattern]
  (let [[match replacement] pattern]
    (when (re-find match s)
      (string/replace s match replacement))))


(defn singular [s]
  (if (string? s)
    (let [match (->> singular-patterns
                     (keep #(replace-pattern s %))
                     (first))]
      (or match s))
    s))


(def plural-patterns
  [[#"(?i)(ax|test)is$" "$1es"]
   [#"(?i)(octop|vir)us$" "$1i]"]
   [#"(?i)(alias|status)$" "$1es"]
   [#"(?i)(bu)s$" "$1ses"]
   [#"(?i)(buffal|tomat)o$" "$1oes"]
   [#"(?i)([ti])um$" "$1a"]
   [#"(?i)sis$" "ses"]
   [#"(?i)(?:([^f])fe|([lr])f)$" "$1$2ves"]
   [#"(?i)(hive)$" "$1s"]
   [#"(?i)(person)$" "people"]
   [#"(?i)([^aeiouy]|qu)y$" "$1ies"]
   [#"(?i)(x|ch|ss|sh)$" "$1es"]
   [#"(?i)(matr|vert|ind)(?:ix|ex)$" "$1ices"]
   [#"(?i)([m|l])ouse$" "$1ice"]
   [#"(?i)^(ox)$" "$1en"]
   [#"(?i)(iz)$" "$1zes"]
   [#"(?i)s$" "s"]
   [#"(?i)$" "s"]])

(defn plural [s]
  (if (string? s)
    (->> plural-patterns
         (keep #(replace-pattern s %))
         (first))
    s))

(defn intern-var
  "Intern a var in the current name space."
  [name value]
  (intern *ns*
          (with-meta (symbol name)
            (meta value))
          value))


(defn depth
  ([val]
   (depth val 0))
  ([val idx]
   (if (sequential? val)
     (depth (first val) (inc idx))
     idx)))

; shamelessly stolen from weavejester/cryptorandom
(defn gen-bytes
  "Returns a random byte array of the specified size."
  [size]
  (let [seed (byte-array size)]
    (.nextBytes (SecureRandom.) seed)
    seed))

(defn base64
  "Return a random base64 string of the specified size in bytes."
  [size]
  (.encodeToString (Base64/getEncoder) (gen-bytes size)))


(defn xhr?
  "Check if a request map `request` has the \"X-Requested-With\" header."
  [request]
  (contains? (:headers request) "x-requested-with"))


(defn namespace* [arg]
  (when (qualified-keyword? arg)
    (namespace arg)))
