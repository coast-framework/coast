(ns coast.alpha.migrations.parser)

(def name-regex #"^--\s*(\w+)\s*$")

(defn name-line? [s]
  (not (nil? (re-matches name-regex s))))

(defn sql-line? [s]
  (nil? (re-matches #"^--.*$" s)))

(defn parse-name [s]
  (when s
    (let [[_ name] (re-matches name-regex s)]
      name)))

(defn parse-query [query-string]
  (let [query-lines (clojure.string/split query-string #"\n")
        name (-> (filter name-line? query-lines)
                 (first)
                 (parse-name))
        sql (clojure.string/join " " (filter sql-line? query-lines))]
    (if (nil? name)
      nil
      {name sql})))

(defn parse [lines]
  (let [query-lines (clojure.string/split lines #"\n\n")]
    (into {} (filter (comp not nil?) (map parse-query query-lines)))))
