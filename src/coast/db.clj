(ns coast.db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [clojure.set]
            [coast.env :as env]
            [coast.db.queries :as queries]
            [coast.db.sql :as db.sql]
            [coast.db.schema :as db.schema]
            [coast.utils :as utils]
            [coast.time :as time]
            [coast.models.sql :as models.sql])
  (:import (java.io File))
  (:refer-clojure :exclude [drop update]))

(defn not-null-constraint [s]
  (let [col (-> (re-find #"null value in column \"(\w+)\" violates not-null constraint" s)
                (second))]
    (if (nil? col)
      {}
      {(keyword col) (str (utils/humanize col) " cannot be blank")})))

(defn unique-constraint [s]
  (let [col (-> (re-find #"(?s)duplicate key value violates unique constraint.*Detail: Key \((.*)\)=\((.*)\)" s)
                (second))]
    (if (nil? col)
      {}
      {(keyword col) (str (utils/humanize col) " is already taken")
       :type :unique-constraint-violation})))

(defmacro transact! [f]
  `(try
     ~f
     (catch org.postgresql.util.PSQLException e#
       (let [msg# (.getMessage e#)
             err1# (not-null-constraint msg#)
             err2# (unique-constraint msg#)
             errors# (merge err1# err2#)]
         (if (empty? errors#)
           (throw e#)
           (throw
            (ex-info
             (str "Invalid data: "
                  (string/join " " (vals errors#)))
             {:type :invalid :errors errors#})))))))

(defn connection []
  (let [db-url (or (env/env :database-url)
                   (env/env :db-spec-or-url))]
    (if (string/blank? db-url)
      (throw (Exception. "Your database connection string is blank. Set the DATABASE_URL or DB_SPEC_OR_URL environment variable"))
      {:connection (jdbc/get-connection db-url)})))

(defn admin-connection []
  (let [db-url (or (env/env :admin-db-spec-or-url)
                   (env/env :admin-database-url)
                   "postgres://localhost:5432/postgres")]
    (if (string/blank? db-url)
      (throw (Exception. "Your admin database connection string is blank. Set the ADMIN_DB_SPEC_OR_URL environment variable"))
      {:connection (jdbc/get-connection db-url)})))

(defn execute! [db sql]
  (jdbc/execute! db sql))

(defn exec [db sql]
  (jdbc/with-db-connection [conn db]
    (with-open [s (.createStatement (jdbc/db-connection conn))]
      (.addBatch s sql)
      (seq (.executeBatch s)))))

(defn sql-vec? [v]
  (and (vector? v)
       (string? (first v))
       (not (string/blank? (first v)))))

(defn query
  ([conn v opts]
   (if (and (sql-vec? v) (map? opts))
     (transact!
       (jdbc/with-db-connection [db-conn conn]
         (jdbc/query db-conn v {:keywordize? true
                                :identifiers utils/kebab})))
     '()))
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
      (throw (ex-info "Record not found" {:type :404}))))

(defq "sql/schema.sql")

(defn create [db-name]
  (let [db-name (format "%s_%s" db-name (env/env :coast-env))
        sql (format "create database %s" db-name)]
    (exec (admin-connection) sql)
    (println "Database" db-name "created successfully")))

(defn drop [db-name]
  (let [db-name (format "%s_%s" db-name (env/env :coast-env))
        sql (format "drop database %s" db-name)]
    (exec (admin-connection) sql)
    (println "Database" db-name "dropped successfully")))

(defn defm [table]
  (create-root-var "insert" (fn [m]
                              (query (connection) (models.sql/insert table m))))
  (create-root-var "update" (fn update-fn
                              ([m where-clause]
                               (query (connection) (models.sql/update table m where-clause)))
                              ([m]
                               (first (query (connection) (models.sql/update table m))))))
  (create-root-var "delete" (fn [m]
                              (let [rows (query (connection) (models.sql/delete table m))]
                                (if (map? m)
                                  (first rows)
                                  rows))))
  (create-root-var "find-by" (fn [m]
                               (let [v (models.sql/v (models.sql/find-by table m) m)]
                                 (first (query (connection) v)))))
  (create-root-var "find" (fn [val]
                            (let [v (models.sql/v (models.sql/find table {:id val}) {:id val})]
                              (first! (query (connection) v)))))
  (create-root-var "query" (fn [& [m]]
                             (query (connection) (models.sql/v (models.sql/query table m)
                                                        (:where m)))))
  (create-root-var "find-or-create-by" (fn [m]
                                        (let [v (models.sql/v (models.sql/find-by table m) m)
                                              row (first (query (connection) v))]
                                          (if (nil? row)
                                            (first (query (connection) (models.sql/insert table m)))
                                            row))))
  nil)

(defn validate-map [schema m]
  (let [ident-ks (clojure.set/intersection (-> m keys set)
                                           (:idents schema))
        col-ks (clojure.set/intersection (-> m keys set)
                                         (:cols schema))
        join-ks (clojure.set/intersection (-> m keys set)
                                          (->> (vals schema)
                                               (filter map?)
                                               (map :db/joins)
                                               (filter some?)
                                               (set)))]
    (merge (select-keys m col-ks) (select-keys m ident-ks)
           (select-keys m join-ks))))

(defn id [conn schema ident]
  (let [sql-vec (db.sql/id schema ident)
        row (-> (query conn sql-vec)
                (first))]
    (get row "id")))

(defn identify-kv [conn schema [k v]]
  (if (db.sql/ident? schema v)
    [(keyword (namespace k) (str (name k) "_id")) (id conn schema v)]
    [k v]))

(defn identify-map [conn schema m]
  (->> (map (fn [[k v]] (identify-kv conn schema [k v])) m)
       (into (empty m))))

(defn qualify-map [k-ns m]
  (->> (map (fn [[k v]] [(keyword k-ns (name k)) v]) m)
       (into (empty m))))

(defn single [coll]
  (if (and (= 1 (count coll))
           (coll? coll))
    (first coll)
    coll))

(defn insert [val]
  (jdbc/with-db-connection [db-conn (connection)]
    (let [schema (db.schema/fetch)
          v (if (map? val) [val] val)
          v (map #(validate-map schema %) v)
          v (map #(assoc % (keyword (-> v first keys first namespace) "updated-at") (time/now)) v)
          v (map #(identify-map db-conn schema %) v)
          sql-vec (db.sql/insert schema v)
          rows (query db-conn sql-vec)]
      (->> (map #(qualify-map (-> v first keys first namespace) %) rows)
           (single)))))

(defn update [m ident]
  (jdbc/with-db-connection [db-conn (connection)]
    (jdbc/with-db-transaction [db-tran db-conn]
      (let [schema (db.schema/fetch)
            k-ns (-> m keys first namespace)
            m (assoc m (keyword k-ns "updated-at") (time/now))
            sql-vec (db.sql/update schema m ident)
            rows (query db-tran sql-vec)]
        (map #(qualify-map (-> ident first namespace) %) rows)))))

(defn upsert [m ident]
  (jdbc/with-db-connection [db-conn (connection)]
    (jdbc/with-db-transaction [db-tran db-conn]
      (let [schema (db.schema/fetch)
            k-ns (-> m keys first namespace)
            m (assoc m (keyword k-ns "updated-at") (time/now))
            sql-vec (db.sql/upsert schema m ident)
            rows (query db-tran sql-vec)]
        (->> (map #(qualify-map (-> ident first namespace) %) rows)
             (single))))))

(defn delete [ident]
  (jdbc/with-db-connection [db-conn (connection)]
    (jdbc/with-db-transaction [db-tran db-conn]
      (let [schema (db.schema/fetch)
            sql-vec (db.sql/delete schema ident)
            row (-> (query db-tran sql-vec)
                    (first))]
        (qualify-map (-> ident first namespace) row)))))
