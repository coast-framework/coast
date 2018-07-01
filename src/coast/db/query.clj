(ns coast.db.query
  (:require [clojure.string :as string]
            [clojure.java.jdbc :as jdbc]
            [coast.env :refer [env]]
            [coast.db.schema :as db.schema]
            [coast.utils :as utils])
  (:refer-clojure :exclude [and or not]))

(defn select-col [k]
  (str (-> k namespace utils/snake) "." (-> k name utils/snake)
       " as "
       (-> k namespace utils/snake) "_" (-> k name utils/snake)))

(defn col [k]
  (str (-> k namespace utils/snake) "." (-> k name utils/snake)))

(defn wrap-str [ws s]
  (if (string/blank? s)
    ""
    (str (first ws)
         s
         (second ws))))

(defn ? [val]
  (cond
    (coll? val) (->> (map (fn [_] "?") val)
                     (string/join ", ")
                     (wrap-str "()"))
    (nil? val) "null"
    :else "?"))

(defn op [val]
  (cond
    (coll? val) "in"
    (nil? val) "is"
    :else "="))

(defn where-clause [s & args]
  (let [ex-s (if (= "where" s)
               "where"
               s)
        s (if (= "where" s)
            "and"
            s)]
    (if (even? (count args))
      (apply conj [] (string/join (str " " s " ") (->> (partition 2 args)
                                                       (filter (fn [[_ v]] (clojure.core/not (clojure.core/and (coll? v) (empty? v)))))
                                                       (map (fn [[k v]] [(col k) (op v) (? v)]))
                                                       (map #(string/join " " %))))
                     (->> (partition 2 args)
                          (map second)
                          (filter #(clojure.core/not (clojure.core/and (coll? %) (empty? %))))))
      (throw (Exception. (str ex-s " requires an even number of args. You passed in " (count args) " args: " (string/join " " args)))))))

(def and (partial where-clause "and"))
(def or (partial where-clause "or"))

(defn one? [arg]
  (clojure.core/and
    (coll? arg)
    (= 1 (count arg))))

(defn where [& args]
  (cond
    (clojure.core/and
     (one? args)
     (vector? (first args))) (let [sql (first (first args))
                                   args (rest (first args))]
                               {:where (str "where " sql)
                                :args args})
    :else (where (apply (partial where-clause "where") args))))

(defn from [s-ks j-ks]
  (when (clojure.core/and (every? qualified-keyword? s-ks)
                          (every? qualified-keyword? j-ks))
    (let [t (-> (map namespace s-ks) (first))
          j (-> (map name j-ks) (first))]
      (str "from " (clojure.core/or j t)))))

(defn select [& args]
  (let [s (->> (map select-col args)
               (string/join ", "))]
    (if (clojure.core/not (string/blank? s))
      {:select (str "select " s)
       :select-ks args}
      (throw (Exception. (str "select needs at least one argument. You typed  : (select)"))))))

(defn limit [i]
  (if (pos-int? i)
    {:limit (str "limit " i)}
    (throw (Exception. (str "limit needs a positive integer. You typed: (limit " i ")")))))

(defn offset [i]
  (if (clojure.core/not (neg-int? i))
    {:offset (str "offset " i)}
    (throw (Exception. (str "offset needs a positive integer. You typed: (offset " i ")")))))

(defn join [k]
  (let [namespace (namespace k)
        name (name k)
        join-left (str namespace "." name "_id")
        join-right (str name ".id")]
    (str "join " namespace " on " join-left " = " join-right)))

(defn joins [& args]
  (let [schema (coast.db.schema/fetch)]
    {:joins (->> (select-keys schema args)
                 (map (fn [[_ v]] (:db/joins v)))
                 (map join)
                 (string/join "\n"))
     :join-ks (->> (select-keys schema args)
                   (map (fn [[_ v]] (:db/joins v))))}))

(defn order [& args]
  {:order (str "order by " (->> (partition-all 2 args)
                                (mapv vec)
                                (mapv #(if (= 1 (count %))
                                         (conj % :asc)
                                         %))
                                (mapv #(str (col (first %)) " " (name (second %))))
                                (string/join ", ")))})

(defn not [v]
  (let [s (string/replace (first v)
                          #"="
                          "!=")
        s (string/replace s
                          #"is null"
                          "is not null")]
    (-> (concat [s] (rest v))
        (vec))))

(defn sql-vec [& params]
  (let [m (apply merge params)
        {:keys [select select-ks join-ks joins where order offset limit args]} m
        sql (->> (filter some? [select (from select-ks join-ks) joins where order offset limit])
                 (string/join "\n"))]
    (apply conj [sql] (filter some? args))))

(defn connection []
  (let [db-url (clojure.core/or (env :database-url)
                   (env :db-spec-or-url))]
    (if (string/blank? db-url)
      (throw (Exception. "Your database connection string is blank. Set the DATABASE_URL or DB_SPEC_OR_URL environment variable"))
      {:connection (jdbc/get-connection db-url)})))

(defn qualify-col [s]
  (let [parts (string/split s #"_")
        k-ns (first parts)
        k-n (->> (rest parts)
                 (string/join "-"))]
    (keyword k-ns k-n)))

(defn query [& params]
  (let [v (apply sql-vec params)]
    (jdbc/with-db-connection [conn (connection)]
      (jdbc/query conn v {:keywordize? false
                          :identifiers qualify-col}))))
