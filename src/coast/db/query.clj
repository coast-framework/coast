(ns coast.db.query
  (:require [clojure.string :as string]
            [clojure.walk]
            [clojure.java.jdbc :as jdbc]
            [coast.db.schema :as db.schema]
            [coast.utils :as utils]
            [coast.db.pg :as pg])
  (:import [org.postgresql.util PGobject]))

(defn select-col [k]
  (str (-> k namespace utils/snake) "." (-> k name utils/snake)
       " as "
       (-> k namespace utils/snake) "$" (-> k name utils/snake)))

(defn sql-vec? [v]
  (and (vector? v)
       (string? (first v))
       (not (string/blank? (first v)))))

(defn col [k]
  (str (-> k namespace utils/snake) "." (-> k name utils/snake)))

(defn wrap-str [ws s]
  (if (string/blank? s)
    ""
    (str (first ws) s (second ws))))

(defn ? [val]
  (cond
    (coll? val) (->> (map (fn [_] "?") val)
                     (string/join ", ")
                     (wrap-str "()"))
    (nil? val) "null"
    :else "?"))

(defn not-op [val]
  (cond
    (sequential? val) "not"
    (nil? val) "is not"
    :else "!="))

(defn op [val]
  (cond
    (sequential? val) "in"
    (nil? val) "is"
    :else "="))

(defn where-part [v]
  (if (vector? v)
    (let [[k op* val] v
          parts (if (= '!= op*)
                  [(col k) (not-op val) (? val)]
                  [(col k) (op op*) (? op*)])]
      (string/join " " parts))
    (throw (Exception. (str "where requires vectors to work. You typed: " v)))))

(defn where-clause [[k v]]
  (string/join (str " " (name k) " ")
               (map where-part v)))

(defn where-op? [k]
  (contains? '#{and or} k))

(defn where-vec [v]
  (let [v (if (vector? (first v))
            (into '[and] v)
            v)
        parts (partition-by where-op? v)
        ops (take-nth 2 parts)
        args (filter #(not (contains? (set ops) %)) parts)]
    (map vector (map first ops) (map vec args))))

(defn where-clauses [v]
  (let [wv (where-vec v)]
    (if (empty? wv)
      (throw (Exception. (str "where only accepts and & or. You typed: " (if (nil? v) "nil" v))))
      (map where-clause wv))))

(defn flat [coll]
  (mapcat #(if (sequential? %) % [%]) coll))

(defn where [v]
  (if (sql-vec? v)
    {:where (str "where " (first v))
     :args (rest v)}
    {:where (str "where " (string/join " and " (map #(wrap-str "()" %) (where-clauses v))))
     :args (->> (filter vector? v)
                (mapv last)
                (filter some?)
                (flat))}))

(defn from [s-ks j-ks]
  (let [t (-> (map #(-> % namespace utils/snake) s-ks) (first))
        j (-> (map #(-> % name utils/snake) j-ks) (first))]
    (str "from " (or j t))))

(defn select [v]
  (let [s (->> (map select-col v)
               (string/join ", "))]
    (if (not (string/blank? s))
      {:select (str "select " s)
       :select-ks v}
      (throw (Exception. (str "select needs at least one argument. You typed :select"))))))

(defn order [v]
  {:order (str "order by " (->> (partition-all 2 v)
                                (mapv vec)
                                (mapv #(if (= 1 (count %))
                                         (conj % 'asc)
                                         %))
                                (mapv #(str (col (first %)) " " (name (second %))))
                                (string/join ", ")))})

(defn limit [[i]]
  (if (pos-int? i)
    {:limit (str "limit " i)}
    (throw (Exception. (str "limit needs a positive integer. You typed: :limit " i)))))

(defn offset [[i]]
  (if (not (neg-int? i))
    {:offset (str "offset " i)}
    (throw (Exception. (str "offset needs a positive integer. You typed: :offset " i)))))

(defn join-col [k]
  (let [namespace (-> k namespace utils/snake)
        name (-> k name utils/snake)]
    (str namespace "." name)))

(defn one-join-statement [k]
  (str (-> k name utils/snake)
       " on "
       (str (-> k name utils/snake) ".id")
       " = "
       (join-col k)))

(defn join-statement [k]
  (str (-> k namespace utils/snake)
       " on "
       (join-col k)
       " = "
       (str (-> k name utils/snake) ".id")))

(defn pull-col [k]
  (str (-> k namespace utils/snake) "$" (-> k name utils/snake)))

(defn json-build-object [k]
  (str "'" (pull-col k) "', " (col k)))

(defn rel-col [k]
  (if (qualified-ident? k)
    (str "'" (pull-col k) "', " (pull-col k))
    (str "'" (-> k first pull-col) "', " (-> k first pull-col))))

(defn namespace* [k]
  (when (qualified-ident? k)
    (namespace k)))

(defn name* [k]
  (when (ident? k)
    (name k)))

(defn rel-key [m]
  (let [k (-> m keys first)]
    (if (sequential? k)
      (-> k first)
      k)))

(defn op? [k]
  (contains? #{:select :pull :joins :where :order :limit :offset :group} k))

(defn sql-map [v]
  (let [parts (partition-by op? v)
        ops (take-nth 2 parts)
        args (filter #(not (contains? (set ops) %)) parts)]
    (zipmap (map first ops) (map vec args))))

(defn rel-opts [m]
  (let [k (-> m keys first)]
    (if (sequential? k)
      (sql-map (drop 1 k))
      {})))

(defn pull-limit [[i]]
  (when (pos-int? i)
    {:limit (str "where rn <= " i)}))

(defn pull-sql-part [[k v]]
  (condp = k
    :order (order v)
    :limit (pull-limit v)
    :else {}))

(defn pull-from [order table]
  (let [order (or order "order by id")]
    (string/join "\n" ["("
                       "select"
                       (str "  " table ".*, ")
                       (str "   row_number() over (" order ") as rn")
                       (str "from " table)
                       (str ") as " table)])))

(defn one-join-col [k]
  (str (-> k name utils/snake) ".id"))

(defn pull-join [schema m]
  (let [k (rel-key m)
        {:keys [db/joins db/ref]} (get schema (keyword k))
        joins (or joins ref)
        {:keys [order limit]} (->> (rel-opts m)
                                   (map pull-sql-part)
                                   (apply merge))
        val (-> m vals first)
        v (filter qualified-ident? val)
        maps (filter map? val)
        child-cols (map #(-> % keys first rel-col) maps)]
    (->> ["left outer join ("
          "select"
          (str (if (nil? joins)
                 (one-join-col (keyword k))
                 (join-col joins)) ",")
          "json_agg(json_build_object("
          (->> (map json-build-object v)
               (concat child-cols)
               (string/join ","))
          (str ") " order ") as " (pull-col k))
          (str "from " (if (nil? joins)
                         (->> k keyword name utils/snake (pull-from order))
                         (->> joins namespace utils/snake (pull-from order))))
          (->> (map #(pull-join schema %) maps)
               (string/join "\n"))
          limit
          (str "group by " (if (nil? joins)
                             (one-join-col (keyword k))
                             (join-col joins)))
          (str ")" (if (nil? joins)
                     (one-join-statement (keyword k))
                     (join-statement joins)))]
         (filter some?)
         (string/join "\n"))))

(defn pull-joins [schema acc v]
  (let [maps (filter map? v)
        joins (map #(pull-join schema %) maps)
        acc (concat acc joins)]
    (if (empty? maps)
     acc
     (pull-joins schema acc (map #(-> % vals first) maps)))))

(defn pull [[v]]
  (let [schema (coast.db.schema/fetch)
        cols (filter qualified-ident? v)
        maps (filter map? v)
        rel-cols (->> (map rel-key maps)
                      (map pull-col))
        col-sql (string/join ", " (concat (map select-col cols)
                                          rel-cols))
        joins (pull-joins schema [] v)]
    {:select (str "select " col-sql)
     :from (str "from " (or (-> cols first namespace* utils/snake)
                            (-> (map rel-key maps) first namespace* utils/snake)))
     :joins (string/join "\n" joins)}))

(defn join [k]
  (str "join " (join-statement k)))

(defn joins [args]
  (let [schema (coast.db.schema/fetch)
        kw-args (map keyword args)]
    {:joins (->> (select-keys schema kw-args)
                 (map (fn [[_ v]] (:db/joins v)))
                 (map join)
                 (string/join "\n"))
     :join-ks (->> (select-keys schema kw-args)
                   (map (fn [[_ v]] (:db/joins v))))}))

(defn group [v]
  {:group (str "group by " (->> (map col v)
                                (string/join ", ")))})

(defn sql-part [[k v]]
  (condp = k
    :select (select v)
    :pull (pull v)
    :joins (joins v)
    :where (where v)
    :order (order v)
    :limit (limit v)
    :offset (offset v)
    :group (group v)))

(defn replace-val [q params val]
  (if (string/starts-with? (str val) "?")
    (let [rk (-> (string/replace-first val #"\?" "")
                 (keyword))
          rv (get params rk)]
      (if (not (contains? params rk))
        (throw (Exception. (str "Parameter " val " is missing in query " q)))
        rv))
    val))

(defn sql-vec
  ([v params]
   (let [m (->> (clojure.walk/prewalk (partial replace-val v params) v)
                (sql-map)
                (map sql-part)
                (apply merge))
         {:keys [select pull select-ks join-ks joins where order offset limit group args]} m
         from-clause (from select-ks join-ks)
         sql (->> (filter some? [select pull (or (:from m) from-clause) joins where order group offset limit])
                  (string/join "\n"))]
     (apply conj [sql] (filter some? args))))
  ([v]
   (sql-vec v nil)))

(extend-protocol jdbc/IResultSetReadColumn
  ;; Covert java.sql.Array to Clojure vector
  java.sql.Array
  (result-set-read-column [val _ _]
    (vec (.getArray val)))

  ;; PGobjects have their own multimethod
  PGobject
  (result-set-read-column [val _ _]
    (pg/read-pgobject val)))
