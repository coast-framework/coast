(ns coast.queries
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [coast.utils :as utils]
            [clojure.set :as set])
  (:import [java.io FileNotFoundException]))

(def name-regex #"^--\s*name\s*:\s*(.+)$")
(def fn-regex #"^--\s*fn\s*:\s*(.+)$")
(def qualified-keyword-pattern #":([\w-\.]+/?[\w-\.]+)Z?")

(defn name-line? [s]
  (not (nil? (re-matches name-regex s))))

(defn fn-line? [s]
  (not (nil? (re-matches fn-regex s))))

(defn sql-line? [s]
  (nil? (re-matches #"^--.*$" s)))

(defn parse-name [s]
  (when s
    (let [[_ name] (re-matches name-regex s)]
      name)))

(defn parse-fn [s]
  (let [[_ f] (re-matches fn-regex (or s ""))]
    (if (nil? f)
      (resolve (symbol "identity"))
      (resolve (symbol f)))))

(defn parse-query-string [query-string]
  (let [query-lines (clojure.string/split query-string #"\n")
        name (-> (filter name-line? query-lines)
                 (first)
                 (parse-name))
        f (->> (filter fn-line? query-lines)
               first
               parse-fn)
        sql (clojure.string/join " " (filter sql-line? query-lines))]
    (if (nil? name)
      nil
      {:sql sql :f f :name name})))

(defn parse [lines]
  (let [query-lines (clojure.string/split lines #"\n\n")]
    (filter #(not (nil? %)) (map parse-query-string query-lines))))

(defn parameterize [s m]
  (string/replace s qualified-keyword-pattern (fn [[_ s]]
                                                (let [k (keyword s)
                                                      v (get m k)]
                                                  (if (coll? v)
                                                    (->> (map (fn [_] (str "?")) v)
                                                         (string/join ","))
                                                    "?")))))

(defn has-keys? [m keys]
  (every? #(contains? m %) keys))

(defn sql-vec [sql m]
  (when (string? sql)
    (let [m (or m {})
          m (utils/map-keys utils/snake m)
          sql-ks (->> (mapv #(-> % second keyword) (re-seq qualified-keyword-pattern sql))
                      (mapv utils/snake))
          params (map #(get m %) sql-ks)
          f-sql (parameterize sql m)
          s-vec (vec (concat [f-sql] (->> (map (fn [val] (if (coll? val) (flatten val) val)) params)
                                          (flatten))))]
      (if (has-keys? m sql-ks)
        s-vec
        (->> (set/difference (set sql-ks) (keys m))
             (string/join ", ")
             (format "Missing keys: %s")
             (Exception.)
             (throw))))))

(defn slurp-resource [filename]
  (or (some-> filename io/resource slurp)
      (throw (FileNotFoundException. filename))))

(defn query [name filename]
  (->> (slurp-resource filename)
       (parse)
       (filter #(= (:name %) name))
       (first)))
