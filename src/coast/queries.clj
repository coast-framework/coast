(ns coast.queries
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [coast.utils :as utils]))

(def name-regex #"^--\s*name\s*:\s*(.+)$")
(def fn-regex #"^--\s*fn\s*:\s*(.+)$")

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
      {name {:sql sql
             :f f}})))

(defn parse [lines]
  (let [query-lines (clojure.string/split lines #"\n\n")]
    (into {} (filter #(not (nil? %)) (map parse-query-string query-lines)))))

(def qualified-keyword-pattern #":([\w-\.]+/?[\w-\.]+)Z?")

(defn parameterize [s m]
  (string/replace s qualified-keyword-pattern (fn [[_ s]]
                                                (let [k (keyword s)
                                                      v (get m k)]
                                                  (if (coll? v)
                                                    (->> (map (fn [_] (str "?")) v)
                                                         (string/join ","))
                                                    "?")))))

(defn sql-vec [sql m]
  (when (string? sql)
    (let [m (or m {})
          m (utils/map-keys utils/snake m)
          sql-ks (mapv #(-> % second keyword) (re-seq qualified-keyword-pattern sql))
          sql-ks (mapv utils/snake sql-ks)
          params (map #(get m %) sql-ks)
          diff (clojure.set/difference (set sql-ks) (set (keys (select-keys m sql-ks))))
          f-sql (parameterize sql m)
          s-vec (vec (concat [f-sql] (->> (map (fn [val] (if (coll? val) (flatten val) val)) params)
                                          (flatten))))]
      (if (empty? diff)
        s-vec
        (throw (Exception. (str "Parameter mismatch. Expected " (string/join ", " (map utils/kebab sql-ks)) ". Got " (string/join ", " (map utils/kebab (keys m))))))))))

(defn parts [filename]
  (let [content (-> filename (string/replace #"^resources/" "") io/resource slurp)]
    (if (or (nil? content)
            (string/blank? content))
      (throw (Exception. (format "%s doesn't exist" filename)))
      (parse content))))
