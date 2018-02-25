(ns coast.alpha.words
  (:require [clojure.string :as string]))

(def exceptions (atom #{}))

(defn exception? [s]
  (when (string? s)
    (contains? @exceptions (string/lower-case s))))

(defn add-exception [word]
  (swap! exceptions conj (string/lower-case word)))

(defn remove-exception [word]
  (swap! exceptions disj (string/lower-case word)))

(defn replace-pattern [s pattern]
  (let [[match replacement] pattern]
    (if (re-find match s)
      (string/replace s match replacement))))

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
   [#"(?i)([^aeiouy]|qu)y$" "$1ies"]
   [#"(?i)(x|ch|ss|sh)$" "$1es"]
   [#"(?i)(matr|vert|ind)(?:ix|ex)$" "$1ices"]
   [#"(?i)([m|l])ouse$" "$1ice"]
   [#"(?i)^(ox)$" "$1en"]
   [#"(?i)(iz)$" "$1zes"]
   [#"(?i)s$" "s"]
   [#"(?i)$" "s"]])

(defn plural [s]
  (if (and (string? s)
           (not (exception? s)))
    (->> plural-patterns
         (keep #(replace-pattern s %))
         (first))
    s))

(def singular-patterns
  [[#"(?i)ies$" "y"]
   [#"(?i)(\w)\1(es)$" "$1"]
   [#"(?i)(tch)(es)$" "$1"]
   [#"(?i)(ss)$" "$1"]
   [#"(?i)s$" ""]])

(defn singular [s]
  (if (and (string? s)
           (not (exception? s)))
    (let [match (->> singular-patterns
                     (keep #(replace-pattern s %))
                     (first))]
      (or match s))
    s))
