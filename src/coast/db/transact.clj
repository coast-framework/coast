(ns coast.db.transact
  (:require [coast.utils :as utils]
            [clojure.string :as string]
            [clojure.set])
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
       (every? qualified-ident? (keys m))))

(defn col
  ([table val]
   (when (clojure.core/ident? val)
     (let [prefix (if (nil? table)
                    ""
                    (str table "."))]
       (->> val name utils/snake
            (str prefix)))))
  ([val]
   (when (qualified-ident? val)
     (let [k-ns (-> val namespace utils/snake)
           k-n (-> val name utils/snake)]
       (str k-ns "." k-n " as " k-ns "_" k-n)))))

(defn ? [m]
  (->> (keys m)
       (map (fn [_] (str "?")))
       (string/join ", ")))

(defn validate-transaction [m]
  (cond
    (not (same-ns? m)) (throw (Exception. "All keys must have the same namespace"))
    (not (qualified-map? m)) (throw (Exception. "All keys must be qualified"))
    :else m))

(defn wrap-str [ws s]
  (if (string/blank? s)
    ""
    (str (first ws) s (second ws))))

(defn insert-into [schema m]
  (let [table (-> m keys first namespace utils/snake)
        rm (clojure.set/rename-keys m (:joins schema))
        s (->> (keys rm)
               (map #(col nil %))
               (string/join ", ")
               (wrap-str "()"))]
    (str "insert into " table s)))

(defn select-col [schema [k v]]
  (if (ident? schema v)
    (str (-> k name (string/replace #"-" "."))
         " as " (-> k name (string/replace #"-" "_")))
    (col k)))

(defn select [schema m]
  (let [s (->> (clojure.set/rename-keys m (:joins schema))
               (map (partial select-col schema))
               (string/join ", "))]
    (str "select " s)))

(defn ident->str [val sep]
  (when (qualified-ident? val)
    (let [k-ns (-> val namespace utils/snake)
          k-n (-> val name utils/snake)]
      (str k-ns sep k-n))))

(defn from-col [schema [k v]]
  (if (ident? schema v)
    (ident->str (first v) "_")
    (col nil k)))

(defn from [schema m]
  (let [table (-> m keys first namespace utils/snake)
        rm (clojure.set/rename-keys m (:joins schema))
        vals-str (->> (? rm) (wrap-str "()"))
        cols-str (->> (map #(from-col schema %) rm) (string/join ", "))]
    (str "from (values " vals-str ") as " table "(" cols-str ")")))

(defn join [[k v]]
  (let [table (-> k namespace utils/snake)]
    (str "join "
         (-> k name utils/snake)
         " on "
         (ident->str (first v) ".")
         " = "
         table "." (ident->str (first v) "_"))))

(defn joins [schema m]
  (let [idents (->> (filter (fn [[_ v]] (ident? schema v)) m)
                    (into {}))]
    (when (not (empty? idents))
      (->> (map join idents)
           (string/join ", ")))))

(defn on-conflict [schema m]
  (let [table (-> m keys first namespace utils/snake)
        rm (clojure.set/rename-keys m (:joins schema))
        idents (->> (get schema :idents)
                    (filter #(= table (namespace %)))
                    (filter #(not= % (keyword table "id"))))
        cols-str (if (or (contains? m (keyword table "id"))
                         (empty? idents))
                   "id"
                   (->> (map #(col nil %) idents)
                        (string/join ",")))
        excluded-cols-str (->> (map #(str (col nil %) " = " (if (= % :updated-at) "now()" (col "excluded" %)))
                                    (-> (apply dissoc rm idents)
                                        (keys)
                                        (conj :updated-at)))
                               (string/join ", "))]
    (str " on conflict (" cols-str ") do update set " excluded-cols-str)))

(defn sql-map [schema m]
  {:insert-into (insert-into schema m)
   :select (select schema m)
   :from (from schema m)
   :joins (joins schema m)
   :on-conflict (on-conflict schema m)})

(defn ident-val [val]
  (if (vector? val)
    (second val)
    val))

(defn sql-vec [schema m]
  (let [m (validate-transaction m)
        map* (into (sorted-map) m)
        returning "returning *"
        {:keys [insert-into select from joins on-conflict]} (sql-map schema map*)
        sql (->> [insert-into select from joins on-conflict returning]
                 (filter some?)
                 (string/join "\n"))
        params (->> (vals map*)
                    (map ident-val))]
    (apply conj [sql] params)))

(defn delete-vec [val]
  (let [v (if (sequential? val) val [val])
        v (map validate-transaction v)
        table (-> v first keys first namespace)
        sql (str "delete from " table
                 " where " (->> v first keys first (col table)) " in "
                 "(" (->> (map ? v)
                          (mapcat identity)
                          (string/join ", "))
                 ")"
                 " returning *")]
    (vec (apply concat [sql] (map #(-> % vals) v)))))
