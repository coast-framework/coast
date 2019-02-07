(ns coast.db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [clojure.data.json :as json]
            [clojure.instant :as instant]
            [coast.env :as env]
            [coast.db.queries :as queries]
            [coast.db.transact :as db.transact]
            [coast.db.connection :refer [connection]]
            [coast.db.update :as db.update]
            [coast.db.insert :as db.insert]
            [coast.db.delete :as db.delete]
            [coast.db.query :as db.query]
            [coast.db.schema]
            [coast.utils :as utils]
            [coast.error :refer [raise rescue]]
            [clojure.java.shell :as shell])
  (:import (java.io File)
           (java.time Instant)
           (java.text SimpleDateFormat))
  (:refer-clojure :exclude [drop update]))

(defn sql-vec? [v]
  (and (vector? v)
       (string? (first v))
       (not (string/blank? (first v)))))

(defn query
  ([conn v opts]
   (if (and (sql-vec? v) (map? opts))
     (jdbc/query (connection) v (merge {:keywordize? true
                                        :identifiers utils/kebab} opts))
     (empty list)))
  ([conn v]
   (query conn v {})))

(defn create-root-var [name value]
  ; shamelessly stolen from yesql
  (intern *ns*
          (with-meta (symbol name)
                     (meta value))
          value))

(defn query-fn [{:keys [sql f]}]
  (fn [& [m]]
    (->> (queries/sql-vec sql m)
         (query (connection))
         (f))))

(defn query-fns [filename]
   (doall (->> (queries/slurp-resource filename)
               (queries/parse)
               (map #(assoc % :ns *ns*))
               (map #(create-root-var (:name %) (query-fn %))))))

(defmacro defq
  ([n filename]
   `(let [q-fn# (-> (queries/query ~(str n) ~filename)
                    (assoc :ns *ns*)
                    (query-fn))]
      (create-root-var ~(str n) q-fn#)))
  ([filename]
   `(query-fns ~filename)))

(defn first! [coll]
  (or (first coll)
      (raise "Record not found" {:coast.router/error :404
                                 :404 true
                                 :type :404
                                 ::error :not-found})))

(defq "sql/schema.sql")


(defn create
 "Creates a new database"
 [s]
 (let [cmd (cond
             (coast.db.connection/sqlite? (env/env :database-url)) "touch"
             (coast.db.connection/pg? (env/env :database-url)) "createdb"
             :else "")
       m (shell/sh cmd s)]
   (if (= 0 (:exit m))
     (str s " created successfully")
     (:err m))))


(defn drop
  "Drops an existing database"
  [s]
  (let [cmd (cond
              (coast.db.connection/sqlite? connection) "rm"
              (coast.db.connection/pg? connection) "dropdb"
              :else "")
        m (shell/sh cmd s)]
    (if (= 0 (:exit m))
      (str s " dropped successfully")
      (:err m))))


(defn single [coll]
  (if (and (= 1 (count coll))
           (coll? coll))
    (first coll)
    coll))

(defn qualify-col [s]
  (let [parts (string/split s #"\$")
        k-ns (first (map #(string/replace % #"_" "-") parts))
        k-n (->> (rest parts)
                 (map #(string/replace % #"_" "-"))
                 (string/join "-"))]
    (keyword k-ns k-n)))

(defn qualify-map [k-ns m]
  (->> (map (fn [[k v]] [(keyword k-ns (name k)) v]) m)
       (into (empty m))))

; TODO fix pull queries for foreign key references
(defn one-first [schema val]
  (if (and (vector? val)
           (= :one (:db/type (get schema (first val))))
           (vector? (second val)))
    [(first val) (first (second val))]
    val))

(defn coerce-inst
  "Coerce json iso8601 to clojure #inst"
  [val]
  (if (string? val)
    (try
      (instant/read-instant-timestamp val)
      (catch Exception e
        val))
    val))

(defn coerce-timestamp-inst
  "Coerce timestamps to clojure #inst"
  [val]
  (if (string? val)
    (try
      (let [fmt (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss")]
        (.parse fmt val))
      (catch Exception e
        val))
    val))

(defn parse-json
  "Parses json from pull queries"
  [schema val]
  (if (and (sequential? val)
           (= 2 (count val))
           (or (= :many (get-in schema [(first val) :db/type]))
               (= :one (get-in schema [(first val) :db/type])))
           (string? (second val)))
    [(first val) (json/read-str (second val) :key-fn qualify-col)]
    val))

(defn q
  ([v params]
   (let [schema (coast.db.schema/fetch)
         rows (query (connection)
                     (db.query/sql-vec v params)
                     {:keywordize? false
                      :identifiers qualify-col})]
     (walk/postwalk #(-> % coerce-inst coerce-timestamp-inst)
        (walk/prewalk #(->> (one-first schema %) (parse-json schema))
          rows))))
  ([v]
   (q v nil)))

(defn select-rels [m]
  (let [schema (coast.db.schema/fetch)]
    (select-keys m (->> (:joins schema)
                        (filter (fn [[_ v]] (qualified-ident? v)))
                        (into {})
                        (keys)))))

(defn resolve-select-rels [m]
  (let [queries (->> (filter (fn [[_ v]] (vector? v)) m)
                     (db.transact/selects))
        ids (->> (filter (fn [[_ v]] (number? v)) m)
                 (mapv (fn [[k v]] [(keyword (namespace k) (name k)) v]))
                 (into {}))
        results (->> (map #(query (coast.db.connection/connection) % {:keywordize? false
                                                                      :identifiers qualify-col})
                          queries)
                     (map first)
                     (apply merge))]
    (merge ids results)))

(defn many-rels [m]
  (select-keys m (->> (coast.db.schema/fetch)
                      (filter (fn [[_ v]] (and (or (contains? v :db/ref) (contains? v :db/joins))
                                               (= :many (:db/type v)))))
                      (map first))))

(defn upsert-rel [parent [k v]]
  (if (empty? v)
    (let [schema (coast.db.schema/fetch)
          jk (or (get-in schema [k :db/joins])
                 (get-in schema [k :db/ref]))
          k-ns (-> jk namespace utils/snake)
          join-ns (-> jk name utils/snake)
          _ (query (connection) [(str "delete from " k-ns " where " join-ns " = ? returning *") (get parent (keyword join-ns "id"))])]
      [k []])
    (let [k-ns (->> v first keys (filter qualified-ident?) first namespace)
          parent-map (->> (filter (fn [[k _]] (= (name k) "id")) parent)
                          (map (fn [[k* v]] [(keyword k-ns (namespace k*)) v]))
                          (into {}))
          v* (mapv #(merge parent-map %) v)
          sql-vec (db.transact/sql-vec v*)
          rows (->> (query (connection) sql-vec)
                    (mapv #(qualify-map k-ns %)))]
      [k rows])))

(defn upsert-rels [parent m]
  (->> (map #(upsert-rel parent %) m)
       (into {})))

(defn transact [m]
  "This function resolves foreign keys (or hydrates), it also deletes related rows based on idents as well as inserting and updating rows.

  Here are some concrete examples:

  Given this schema:

  [{:db/ident :author/name
    :db/type \"citext\"}

   {:db/ident :author/email
    :db/type \"citext\"}

   {:db/rel :author/posts
    :db/joins :post/author
    :db/type :many}

   {:db/col :post/title
    :db/type \"text\"}

   {:db/col :post/body
    :db/type \"text\"}]

  Insert multiple tables at once

  (db/transact {:author/name \"test\"
                :author/email \"test@test.com\"
                :author/posts [{:post/title \"title\"
                                :post/body \"body\"}]})

  or just one

  (db/transact {:author/name \"test2\"
                :author/email \"test2@test.com\"})

  Retrieve nested rows

  (db/pull '[author/id author/name author/email
             {:author/posts [post/id post/title post/body]}]
           [:author/name \"test\"])

  Update with the same command

  (db/transact {:post/id 1
                :post/author [:author/name \"test2\"]})

  or the equivalent

  (db/transact {:post/id 1
                :post/author 2})

  Delete multiple nested rows with one function

  (db/transact {:author/id 2
                :author/posts []})"

  (let [k-ns (->> m keys (filter qualified-ident?) first namespace)
        s-rels (select-rels m)
        s-rel-results (resolve-select-rels s-rels) ; foreign keys
        m-rels (many-rels m)
        m* (merge m s-rel-results)
        m* (apply dissoc m* (keys m-rels))
        row (when (not (empty? (db.update/idents (map identity m*))))
              (->> (db.update/sql-vec m*)
                   (query (connection))
                   (map #(qualify-map k-ns %))
                   (single)))
        row (if (empty? row)
              (->> (db.insert/sql-vec m*)
                   (query (connection))
                   (map #(qualify-map k-ns %))
                   (single))
              row)
        rel-rows (upsert-rels row m-rels)]
    (merge row rel-rows)))

(defn insert [arg]
  (let [m (if (sequential? arg) (first arg) arg)
        k-ns (-> m keys first namespace utils/snake)]
    (->> (db.insert/sql-vec arg)
         (query (connection))
         (map #(qualify-map k-ns %))
         (single))))

(defn update* [m]
  (let [k-ns (-> m keys first namespace utils/snake)]
    (->> (db.update/sql-vec m)
         (query (connection))
         (map #(qualify-map k-ns %))
         (single))))

(defn delete [arg]
  (let [v (db.delete/sql-vec arg)
        k-ns (if (sequential? arg)
               (-> arg first keys first namespace utils/snake)
               (-> arg keys first namespace utils/snake))]
    (->> (query (connection) v)
         (map #(qualify-map k-ns %))
         (single))))

(defn pull [v ident]
  (first (q [:pull v :where ident])))


(defn -main [& [action db-name]]
  (case action
    "create" (println (create (or db-name (env/env :dev-db))))
    "drop" (println (drop (or db-name (env/env :dev-db))))
    "")
  (System/exit 0))
