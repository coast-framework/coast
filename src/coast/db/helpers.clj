(ns coast.db.helpers
  (:require [coast.utils :as utils]
            [clojure.string :as string])
  (:refer-clojure :exclude [update]))


(defn nested-map? [m]
  (when (map? m)
    (some coll? (vals m))))


(defn dot-ident? [k]
  (when (ident? k)
    (string/includes? (name k) ".")))


(defn table [m]
  (cond
    (keyword? m) (name m)

    (nested-map? m) (-> m keys first name)

    (and (map? m)
      (every? qualified-ident? (keys m)))
    (-> m keys first namespace)

    (and (map? m)
      (some dot-ident? (keys m)))
    (-> m keys first name (string/split #"\.") first)

    :else nil))


(defn column [k]
  (when (ident? k)
    (cond
      (dot-ident? k) (-> k name (string/split #"\.") last)
      :else (name k))))


(defn qualify-nested-map [m]
  (if (nested-map? m)
    (let [table (table m)
          m (-> m vals utils/flat)]
      (map (partial utils/map-keys #(keyword table (column %))) m))
    m))


(defn insert-all
  "Builds an insert keyword sql vector from a map or sequence of maps.
   All maps need to have the same keys."
  [v]
  (when (or (map? v)
            (and (vector? v)
                 (every? map? v)))
    (let [table (or (table v) (table (first v)))
          v (if (map? v)
              (qualify-nested-map v)
              (map qualify-nested-map v))
          cols (->> (mapcat identity v)
                    (map first)
                    (map column)
                    (map #(keyword table %))
                    (distinct))
           values (->> (map vals v))]
      (concat
       (conj cols :insert)
       (conj values :values)))))


(defn insert
  "Returns an keyword sql vector from a map"
 [m]
 (if (nested-map? m)
   (insert-all m)
   (insert-all [m])))


(defn update-all
  "Builds a keyword sql vector from a sequence of maps.
   All maps need to have the same primary key column and the same set of keys."
  [maps where]
  (let [maps (if (map? maps)
              (qualify-nested-map maps)
              (map qualify-nested-map maps))
        m (first maps)
        table (-> m table keyword)]
    (concat
      [:update table
       :where (vec (mapcat identity where))]
      (conj (->> (map (partial utils/map-keys column) maps)
                 (map (partial utils/map-keys keyword))
                 (mapcat identity)
                 (distinct))
            :set))))


(defn update
  "Returns a keyword sql vec from a map."
  [m where]
  (if (nested-map? m)
    (update-all m where)
    (update-all [m] where)))


(defn upsert-all
  "Returns a keyword sql vec from a sequence of maps and unique-by column(s)"
  [v unique-by]
  (let [columns ()
        on-conflict unique-by]
    [:insert columns
     :values values
     :on-conflict on-conflict
     :do-update-set excluded-cols]))




(defn upsert
  "Builds an upsert keyword sql vector from a map or sequence of maps.
   All maps need to have the same keys."
  [val opts]
  (when (nil? val)
    (throw (Exception. "coast/upsert was called with nil")))
  (let [v (utils/vectorize val)
        cols (->> (mapcat identity v)
                  (map first)
                  (distinct))
        unqualified-ks (filter #(not (qualified-ident? %)) cols)
        _ (when (not (empty? unqualified-ks))
            (throw (Exception. "coast/upsert requires all maps to have qualified keys. The namespace is used to determine the table to insert into.")))
        distinct-namespaces (distinct
                             (map namespace cols))
        _ (when (not= 1 (count distinct-namespaces))
            (throw (Exception. (str "coast/upsert found multiple namespaces. "
                                    "Which table did you want to insert into?"
                                    (string/join ", " distinct-namespaces)))))
        values (map #(map (fn [x] (second x)) %) v)
        excluded-cols (->> (filter #(not (contains? (set (:on-conflict opts)) (name %))) cols)
                           (map #(vector % (str "excluded." (name %)))))]
    (concat
     (conj cols :insert)
     (conj values :values)
     (conj excluded-cols :do-update-set)
     (conj (:on-conflict opts) :on-conflict))))


(defn delete-all
  "Builds a keyword sql vector from a sequence of maps"
  [t where]
  [:delete
   :from (table t)
   :where (vec (mapcat identity where))])


(defn delete
  "Returns a keyword sql vec from a map"
  [t where]
  (delete-all t where))


(comment
  (upsert {:account {:name "hello" :id 1}} {:unique-by :id})

  (upsert-all {:account [{:a 1 :id 1}
                         {:b 2 :id 2}]}
    {:unique-by :id})

  (coast.db.sql/sql-vec {}
    (delete-all :a
      {:name ["whatever" "whatever2"]}))

  (coast.db.helpers/update! {:account {:name "new"}}
    {:name "old"})

  (coast.db.sql/sql-vec {}
    (coast.db.helpers/update-all {:account {:name "new"}}
      {:name ["old" "old1"]})))


(defn dup [num l]
  (let [frst (first l)
        lst (last l)]
    (mapcat #(if (and (not= frst %)
                      (not= lst %))
               (repeat num %)
               (list %))
      l)))


(defn fetch [& args]
  (let [tables (filter ident? args)
        from (last tables)
        joins (when (> (count tables) 1)
                (->> (dup 2 tables)
                     (partition-all 2)
                     (reverse)
                     (mapcat identity)
                     (concat [:joins])))
        ids (filter (comp not ident?) args)
        where (when (>= (count ids) 1)
                (->> (partition 2 args)
                     (mapv #(vector (-> % first name (str ".id") keyword)
                                    (second %)))))
        where (when (not (empty? where))
                [:where where])]
    (vec
      (concat
        [:select :*
         :from from]
        joins
        where))))
