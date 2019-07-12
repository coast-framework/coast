(ns coast.db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [clojure.data.json :as json]
            [clojure.instant :as instant]
            [coast.db.queries :as queries]
            [coast.db.transact :as db.transact]
            [coast.db.connection :as db.connection :refer [connection spec]]
            [coast.db.update :as db.update]
            [coast.db.insert :as db.insert]
            [coast.db.schema :as db.schema]
            [coast.db.sql :as sql]
            [coast.db.helpers :as helpers]
            [coast.migrations :as migrations]
            [coast.utils :as utils]
            [error.core :as error]
            [clojure.java.shell :as shell]
            [clojure.java.io :as io])
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
     (jdbc/query conn v (merge {:keywordize? true
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
      (error/raise "Record not found" :404)))

(defn create
 "Creates a new database"
 ([s]
  (let [s (if (string/blank? s) (spec :database) s)
        cmd (cond
              (= (spec :adapter) "sqlite") "touch"
              (= (spec :adapter) "postgres") "createdb"
              :else "")
        m (shell/sh cmd s)]
    (if (= 0 (:exit m))
      (str s " created successfully")
      (:err m))))
 ([]
  (create (spec :database))))


(defn drop
  "Drops an existing database"
  ([s]
   (let [s (if (string/blank? s) (spec :database) s)
         cmd (cond
               (= (spec :adapter) "sqlite") "rm"
               (= (spec :adapter) "postgres") "dropdb"
               :else "")
         m (shell/sh cmd s)]
     (if (= 0 (:exit m))
       (str s " dropped successfully")
       (:err m))))
  ([]
   (drop (spec :database))))



(defn single [coll]
  (if (and (= 1 (count coll))
           (coll? coll))
    (first coll)
    coll))

(defn qualify-col [s]
  (if (.contains s "$")
    (let [parts (string/split s #"\$")
          k-ns (first (map #(string/replace % #"_" "-") parts))
          k-n (->> (rest parts)
                   (map #(string/replace % #"_" "-"))
                   (string/join "-"))]
      (keyword k-ns k-n))
    (keyword s)))

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
  [associations val]
  (if (and (sequential? val)
           (= 2 (count val))
           (or (contains? (get associations (first val)) :has-many)
               (contains? (get associations (first val)) :belongs-to))
           (string? (second val)))
    [(first val) (json/read-str (second val) :key-fn qualify-col)]
    val))


(def col-query
  {"sqlite" ["select
               m.name as table_name,
               p.name as column_name
              from sqlite_master m
              left outer join pragma_table_info((m.name)) p on m.name <> p.name
              order by table_name, column_name"]
   "postgres" ["select table_name, column_name
                from information_schema.columns
                order by table_name, column_name"]})


(defn col-map [conn adapter]
  (let [rows (jdbc/query conn (get col-query adapter))]
    (->> (group-by :table_name rows)
         (mapv (fn [[k v]]
                 [(keyword (utils/kebab-case k)) (->> (map :column_name v)
                                                      (map utils/kebab-case)
                                                      (map #(keyword (utils/kebab-case k) %)))]))
         (into {}))))


(defmacro transaction [binder & body]
  `(jdbc/with-db-transaction [~binder (connection)]
     ~@body))


(defn sql-vec
  ([conn v params]
   (let [conn (or conn (connection))
         {:keys [adapter]} (db.connection/spec)
         associations-fn (load-string (slurp (or (io/resource "associations.clj")
                                                 "db/associations.clj")))
         associations (if (some? associations-fn)
                        (associations-fn)
                        {})
         col-map (col-map conn adapter)]
      (if (sql-vec? v)
        v
        (sql/sql-vec adapter col-map associations v params))))
  ([v params]
   (if (and (vector? v)
            (map? params))
     (sql-vec nil v params)
     (sql-vec v params {})))
  ([v]
   (sql-vec nil v nil)))


(defn q
  ([conn v params]
   (let [conn (or conn (connection))
         {:keys [adapter debug]} (db.connection/spec)
         associations-fn (load-string (slurp (or (io/resource "associations.clj")
                                                 "db/associations.clj")))
         associations (if (some? associations-fn)
                        (associations-fn)
                        {})
         col-map (col-map conn adapter)
         sql-vec (if (sql-vec? v)
                   v
                   (sql/sql-vec {:adapter adapter
                                 :col-map col-map
                                 :associations associations}
                    v params))
         _ (when (true? debug)
             (println sql-vec))
         rows (query conn
                     sql-vec
                     {:keywordize? false
                      :identifiers qualify-col})]
     (walk/postwalk #(-> % coerce-inst coerce-timestamp-inst)
        (walk/prewalk #(->> (one-first associations %) (parse-json associations))
          rows))))
  ([v params]
   (if (and (vector? v)
            (map? params))
     (q nil v params)
     (q v params {})))
  ([v]
   (q nil v nil)))


(defn execute!
  ([conn v params]
   (let [conn (or conn (connection))
         {:keys [adapter debug]} (db.connection/spec)
         sql-vec (sql/sql-vec adapter {} {} v params)]
      (when (true? debug)
        (println sql-vec))
      (jdbc/execute! conn sql-vec)))
  ([v params]
   (if (and (vector? v)
            (map? params))
     (execute! nil v params)
     (execute! v params {})))
  ([v]
   (execute! nil v {})))


(defn fetch
  "get-in but for your database"
  [& args]
  (let [v (apply helpers/fetch args)
        rows (q (connection) v)]
    (if (= (count rows) 1)
      rows
      (first rows))))


(defn find-by
  ([conn k m]
   (when (and (ident? k)
              (map? m))
     (first
       (q conn [:select :*
                :from k
                :where (mapv identity m)
                :limit 1]
               {}))))
  ([k m]
   (find-by nil k m)))


(defn insert
  ([conn arg]
   (let [{:keys [adapter]} (db.connection/spec)
         v (helpers/insert arg)]
     (condp = adapter
       "sqlite" (jdbc/with-db-transaction [c (or conn (connection))]
                  (execute! c v)
                  (let [{id :id} (first (q c ["select last_insert_rowid() as id"]))
                        table (if (sequential? arg)
                                (-> arg first keys first namespace)
                                (-> arg keys first namespace))]
                    (fetch c (keyword table) id)))
       "postgres" (let [v (conj v :returning :*)]
                    (q conn v)))))
  ([arg]
   (insert nil arg)))


(defn update
  ([conn arg]
   (let [{:keys [adapter]} (db.connection/spec)
         v (helpers/update arg)]
     (condp = adapter
       "sqlite" (jdbc/with-db-transaction [c (or conn (connection))]
                  (execute! c v)
                  (let [table (if (sequential? arg)
                                (-> arg first keys first namespace)
                                (-> arg keys first namespace))
                        id (if (sequential? arg)
                             (get-in arg [0 (keyword table "id")])
                             (get arg (keyword table "id")))]
                    (fetch c (keyword table) id)))
       "postgres" (let [v (conj v :returning :*)]
                    (q conn v)))))
  ([arg]
   (update nil arg)))


(defn unique-column-names [s]
  (->> (string/split s #",")
       (filter #(string/includes? % "unique"))
       (map string/trim)
       (map #(string/split % #" "))
       (map first)))


(defn upsert
  ([conn arg opts]
   (let [table-name (if (sequential? arg)
                      (-> arg first keys first utils/namespace*)
                      (-> arg keys first utils/namespace*))
         {:keys [adapter]} (db.connection/spec)]

     (when (nil? table-name)
       (throw (Exception. "coast/upsert expects a map with qualified keywords")))

     (condp = adapter
       "sqlite" (jdbc/with-db-transaction [c (or conn (connection))]
                  (let [{sql :sql} (first (q c ["select sql from sqlite_master where type = ? and name = ?" "table" table-name]))
                        on-conflict (if (list? (:on-conflict opts))
                                      (:on-conflict opts)
                                      (unique-column-names sql))
                        v (helpers/upsert arg {:on-conflict on-conflict})]
                    (execute! c v)
                    (let [{id :id} (first (q c ["select last_insert_rowid() as id"]))
                          id (if (zero? id)
                               (get (find-by c (keyword table-name) (select-keys arg (mapv #(keyword table-name %) on-conflict)))
                                    (keyword table-name "id"))
                               id)
                          table (if (sequential? arg)
                                  (-> arg first keys first namespace)
                                  (-> arg keys first namespace))]
                      (fetch c (keyword table) id))))
       "postgres" (jdbc/with-db-transaction [c (or conn (connection))]
                    (let [{indexdef :indexdef} (first (q c ["select indexdef from pg_indexes where tablename = ?" table-name]))
                          on-conflict (-> (re-find #"\((.*)\)" indexdef)
                                          (last)
                                          (string/split #","))
                          v (helpers/upsert arg {:on-conflict on-conflict})
                          v (conj v :returning :*)]
                      (q c v))))))
  ([arg]
   (upsert nil arg {})))


(defn delete
  ([conn arg]
   (let [{:keys [adapter]} (db.connection/spec)
         v (helpers/delete arg)]
     (condp = adapter
       "sqlite" (first
                 (execute! conn v))
       "postgres" (let [v (conj v :returning :*)]
                    (q conn v)))))
  ([arg]
   (delete nil arg)))


(defn not-empty? [val]
  ((comp not empty) val))


(defn pull [v ident]
  (not-empty?
    (q [:pull v
        :from (-> ident first namespace)
        :where ident])))


(defn any-rows? [table]
  (not-empty?
   (q [:select :*
       :from (keyword table)
       :limit 1])))


(def migrate migrations/migrate)
(def rollback migrations/rollback)

(defn -main [& [action db-name]]
  (case action
    "create" (println (create (or db-name (spec :database))))
    "drop" (println (drop (or db-name (spec :database))))
    "")
  (System/exit 0))
