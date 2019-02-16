(ns coast.db.sql
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [coast.utils :as utils])
  (:refer-clojure :exclude [update])
  (:import (java.time LocalDateTime)))


(def ops #{:select :from :update :set :insert :delete :pull :joins :where :order :limit :offset :group :values})


(defn op? [k]
  (contains? ops k))


(defn ? [val]
  (cond
    (and (coll? val)
         (every? coll? val)) (->> (map ? val)
                                  (string/join ", "))
    (coll? val) (->> (map ? val)
                     (string/join ", ")
                     (utils/surround "()"))
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
    (= 'like val) "like"
    (= :like val) "like"
    :else "="))


(defn select-col [k]
  (if (qualified-ident? k)
    (str (utils/sqlize k)
         " as "
         (-> k namespace utils/snake-case) "$" (-> k name utils/snake-case))
    (utils/sqlize k)))


(defn select [v]
  (let [s (->> (map select-col v)
               (string/join ", "))]
    (if (not (string/blank? s))
      {:select (str "select " s)
       :select-ks v}
      (throw (Exception. (str "select needs at least one argument."))))))


(defn where-part [v]
  (if (not (vector? v))
    (throw (Exception. (str "where requires vectors to work. You typed: " v)))
    (if (utils/sql-vec? v)
      (first v)
      (let [[k op* val] v
            parts (if (= '!= op*)
                    [(utils/sqlize k) (not-op val) (? val)]
                    [(utils/sqlize k) (op op*) (? op*)])]
        (string/join " " parts)))))


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


(defn last-or-rest [v]
  (if (utils/sql-vec? v)
    (rest v)
    (last v)))


(defn where [v]
  (if (utils/sql-vec? v)
    {:where (str "where " (first v))
     :args (rest v)}
    {:where (str "where " (string/join " and " (map #(utils/surround "()" %) (where-clauses v))))
     :args (->> (filter vector? v)
                (mapv last-or-rest)
                (filter some?)
                (utils/flat))}))


(defn insert [v]
  (let [table (-> v first namespace utils/snake-case)]
    {:insert (str "insert into " table " ("
                  (->> (map name v)
                       (map utils/snake-case)
                       (string/join ", "))
                 ")")}))


(defn values [v]
  {:values (str "values " (? v))
   :args v})


(defn update [v]
  (let [table (-> v first utils/snake-case)]
    {:update (str "update " table)}))


(defn update-set [v]
  (let [v (conj v [:updated-at (LocalDateTime/now)])
        args (filter #(not= "id" (-> % first name)) v)]
    {:update-set (str "set " (->> (map (fn [[k _]] (str (-> k name utils/snake-case) " = ?")) args)
                                  (distinct)
                                  (string/join ", ")))
     :update-set-args (map second args)}))


(defn from [v]
  {:from (str "from " (string/join " " (map name v)))})


(defn delete [v]
  (merge (from v) {:delete (str "delete")}))


(defn limit [[i]]
  (if (pos-int? i)
    {:limit (str "limit " i)}
    (throw (Exception. (str "limit needs a positive integer. You typed: :limit " i)))))


(defn offset [[i]]
  (if (not (neg-int? i))
    {:offset (str "offset " i)}
    (throw (Exception. (str "offset needs a positive integer. You typed: :offset " i)))))


(defn group [v]
  {:group (str "group by " (->> (map utils/sqlize v)
                                (string/join ", ")))})


(defn order [v]
  {:order (str "order by " (->> (partition-all 2 v)
                                (mapv vec)
                                (mapv #(if (= 1 (count %))
                                         (conj % 'asc)
                                         %))
                                (mapv #(str (utils/sqlize (first %)) " " (name (second %))))
                                (string/join ", ")))})


(defn join [m]
  (str "join " (name (:join/table m))
       " on " (utils/sqlize (:join/left m))
       " = " (utils/sqlize (:join/right m))))


(defn join-map [from k]
  {:join/table (utils/sqlize k)
   :join/left (utils/sqlize (keyword (name k) from))
   :join/right (utils/sqlize (keyword from "id"))})


(defn joins [m]
  (let [from (utils/sqlize (first (:from m)))]
    (assoc m :joins (->> (map #(join-map from %) (:joins m))
                         (map join)
                         (string/join " ")))))


(defn no-asterisks? [m]
  (empty? (filter #(= (name %) "*") (:select m))))


(defn ns-or-name [k]
  (or (namespace k) (name k)))


(defn select-ns-keys [key-ns m]
  (->> (filter (fn [[k _]] (qualified-ident? k)) m)
       (filter (fn [[k _]] (= (name key-ns) (namespace k))))
       (into {})))


(def pull-vec-hack (atom nil))
(defn pull-vec [k p-vec cm am]
  (if (empty? cm)
    p-vec
    (let [next-p-vec (if (empty? p-vec)
                        (get cm k)
                        p-vec)
          next-p-vec (clojure.walk/postwalk
                      (fn [val]
                        (if (contains? am val)
                          (do
                            (reset! pull-vec-hack val)
                            {val (get cm (:has-many (get am val)))})
                          val))
                      next-p-vec)]
      (pull-vec (first (keys cm))
        next-p-vec
        (dissoc cm k)
        (dissoc am @pull-vec-hack)))))

(defn expand-pull-asterisk [associations col-map m]
  (if (and (contains? m :pull)
           (= "*" (name (if (vector? (:pull m))
                          (first (:pull m))
                          (:pull m))))
           (not (empty? associations)))
    (let [table (keyword (name (first (:from m))))
          pull (pull-vec table [] col-map associations)]
      (assoc m :pull pull))
    m))


(defn expand-select-asterisks [col-map m]
  (if (or (no-asterisks? m)
          (not (contains? m :select)))
    m
    (let [from-tables (map ns-or-name (:from m))
          joins-tables (map ns-or-name (:joins m))
          select-tables (map namespace (filter qualified-ident? (:select m)))
          tables (->> (concat from-tables joins-tables select-tables)
                      (filter some?)
                      (map name)
                      (map keyword))
          columns (mapcat identity
                          (map #(get col-map %) tables))]
      (assoc m :select (->> (concat (:select m) columns)
                            (filter #(not= (name %) "*"))
                            (vec))))))


(defn replace-val [q params val]
  (if (and (ident? val)
           (string/starts-with? (str val) "?"))
    (let [rk (-> (string/replace-first val #"\?" "")
                 (keyword))
          rv (get params rk)]
      (if (not (contains? params rk))
        (throw (Exception. (str "Parameter " val " is missing in query " q)))
        rv))
    val))


(defn replace-vals [v params]
  (walk/prewalk (partial replace-val v params) v))


(defn sql-map [v]
  (let [parts (partition-by op? v)
        ops (take-nth 2 parts)
        args (filter #(not (contains? (set ops) %)) parts)]
    (zipmap (map first ops) (map vec args))))

(defn sql-part [adapter [k v]]
  (condp = k
    :select (select v)
    :from (from v)
    ;:pull {:pull v}
    :joins {:joins v}
    :where (where v)
    :order (order v)
    :limit (limit v)
    :offset (offset v)
    :group (group v)
    :delete (delete v)
    :values (values v)
    :insert (insert v)
    :update (update v)
    :set (update-set v)
    nil))

(defn sql-vec
  "Generates a jdbc sql vector from an ident sql vector"
  [adapter col-map associations v params]
  (let [m (->> (replace-vals v params)
               (sql-map)
               (expand-select-asterisks col-map)
               (expand-pull-asterisk associations col-map)
               (joins)
               (map #(sql-part adapter %))
               (apply merge))
        {:keys [select pull joins
                where order offset
                limit group args delete
                insert values update from
                update-set update-set-args]} m
        sql (->> (filter some? [select pull delete from update update-set insert values joins where order offset limit group])
                 (string/join " "))]
    (apply conj [sql] (concat update-set-args (filter some? (utils/flat args))))))
