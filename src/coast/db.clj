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
            [coast.error :refer [raise rescue]]
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

(defn first!
  "Return the not-nil first record or a 404 response"
  [coll]
  (or (first coll)
      (raise "Record not found" {:coast.router/error :404
                                 :404                true
                                 :not-found          true
                                 :type               :404
                                 ::error             :not-found})))

(defn create-sqlite
  "Creates the sqlite database on disk."
  [database]
  (shell/sh "touch" database))

(defn presence
  "Returns a string if it is not blank, otherwise returns nil."
  [s]
  (if (string/blank? s) nil s))

(defn env|spec
  "Finds a value in the env first and falls back to the spec."
  [env-name spec-name]
  (or (presence (System/getenv env-name))
      (presence (spec spec-name))))

(defn create-postgres-command
  "Generates the vector that makes up the clojure command."
  [database]
  (let [host (env|spec "PGHOST" :host)
        port (env|spec "PGPORT" :port)
        username (env|spec "PGUSER" :username)
        password (env|spec "PGPASSWORD" :password)]
    (cond-> ["createdb"]
      host (concat ["--host" host])
      port (concat ["--port" port])
      username (concat ["--username" username])
      true (concat [database])
      password (concat [:env {"PGPASSWORD" password}]))))

(defn create-postgres
  "Runs the postgres createdb command with arguments based on the spec."
  [database]
  (apply shell/sh (create-postgres-command database)))

(defn create
  "Creates a new database."
  ([s]
   (let [s (if (string/blank? s) (spec :database) s)
         result (case (spec :adapter)
                  "sqlite" (create-sqlite s)
                  "postgres" (create-postgres s)
                  {:err (str "Error: Unknown adapter " (spec :adapter))})]
     (if (= 0 (:exit result))
       (str s " create successfully")
       (:err result))))
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


(defmacro transaction
  "Evaluate the body in the context of database connection defined in the `db.edn`."
  [binder & body]
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
  "Run a query.

  For example:

  ```clojure
  (coast/q '[:select *
           :from person
           :where [screen-name ?screen-name]
           :limit 1]
         {:screen-name \"@sean\"})
  ```
  Also see [Queries](https://coastonclojure.com/docs/queries.md)"
  ([conn v params]
   (let [conn                    (or conn (connection))
         {:keys [adapter debug]} (db.connection/spec)
         associations-fn         (load-string (slurp (or (io/resource "associations.clj")
                                                         "db/associations.clj")))
         associations            (if (some? associations-fn)
                                   (associations-fn)
                                   {})
         col-map                 (col-map conn adapter)
         sql-vec                 (if (sql-vec? v)
                                   v
                                   (sql/sql-vec adapter col-map associations v params))
         _                       (when (true? debug)
                                   (println sql-vec))
         rows                    (query conn
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
  "Perform a general SQL operation.
  This includes: `:insert`, `:delete`, `:update`..."
  ([conn v params]
   (let [conn                    (or conn (connection))
         {:keys [adapter debug]} (db.connection/spec)
         sql-vec                 (sql/sql-vec adapter {} {} v params)]
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


(defn pluck
  "Takes a query and returns the first result, which is kind of weird, but that's what it's called

  Example:

  ```clojure
  (coast/pluck [:select * :from person :where [id 1]])
  ```
  "
  ([conn v params]
   (first
     (q conn v params)))
  ([v params]
   (if (and (vector? v)
            (map? params))
     (pluck nil v params)
     (pluck v params {})))
  ([v]
   (pluck nil v {})))


(defn fetch
  "Returns a given row by primary key.

  Example:

  ```clojure
  (coast/fetch :person 1)
  ; => {:person/first-name \"Johnny\" :person/last-name \"Appleseed\"}
  ```"
  ([conn k id]
   (when (and (ident? k)
              (some? id))
     (first
       (q conn '[:select *
                 :from ?from
                 :where [id ?id]
                 :limit 1]
          {:from k
           :id   id}))))
  ([k id]
   (fetch nil k id)))



(defn find-by
  "Find the first record that matches map `m` from table `k`"
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


(defn select-rels [m]
  (let [schema (db.schema/fetch)]
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
        results (->> (map #(query (db.connection/connection) % {:keywordize? false
                                                                :identifiers qualify-col})
                          queries)
                     (map first)
                     (apply merge))]
    (merge ids results)))

(defn many-rels [m]
  (select-keys m (->> (db.schema/fetch)
                      (filter (fn [[_ v]] (and (or (contains? v :db/ref) (contains? v :db/joins))
                                               (= :many (:db/type v)))))
                      (map first))))

(defn upsert-rel [parent [k v]]
  (if (empty? v)
    (let [schema (db.schema/fetch)
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

(defn transact
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
  [m]
  (let [k-ns          (->> m keys (filter qualified-ident?) first namespace)
        s-rels        (select-rels m)
        s-rel-results (resolve-select-rels s-rels) ; foreign keys
        m-rels        (many-rels m)
        m*            (merge m s-rel-results)
        m*            (apply dissoc m* (keys m-rels))
        row           (when (not (empty? (db.update/idents (map identity m*))))
                        (->> (db.update/sql-vec m*)
                             (query (connection))
                             (map #(qualify-map k-ns %))
                             (single)))
        row           (if (empty? row)
                        (->> (db.insert/sql-vec m*)
                             (query (connection))
                             (map #(qualify-map k-ns %))
                             (single))
                        row)
        rel-rows      (upsert-rels row m-rels)]
    (merge row rel-rows)))

(defn insert
  "Insert a record to the db.

  Example:

  ```clojure
  (coast/insert {:person/email \"test@example.com\" :person/screen-name \"test\"})
  ```

  You can also insert multiple records at once:

  ```clojure
  (coast/insert [{:person/email \"test1@test.com\" :person/screen-name \"test1\"}
                 {:person/email \"test2@test.com\" :person/screen-name \"test2\"}])
  ```

  See [Insert](https://coastonclojure.com/docs/queries.md#user-content-inserts)"
  ([conn arg]
   (let [{:keys [adapter]} (db.connection/spec)
         v                 (helpers/insert arg)]
     (condp = adapter
       "sqlite"   (if (nil? conn)
                    (transaction c
                                 (execute! c v)
                                 (let [{id :id} (pluck c ["select last_insert_rowid() as id"])
                                       table    (if (sequential? arg)
                                                  (-> arg first keys first namespace)
                                                  (-> arg keys first namespace))]
                                   (fetch c (keyword table) id)))
                    (execute! conn v))
       "postgres" (let [v (conj v :returning :*)]
                    (q conn v)))))
  ([arg]
   (insert nil arg)))


(defn update
  "Update an record.

  `update` requires an `:id` key

  ```clojure
  (coast/update {:person/id 1 :person/last-name \"Appleseed\" :person/first-name \"Johnny\"})
  ```

  It can also take a list of maps:

  ```clojure
  (coast/update [{:person/id 1 :person/last-name \"Appleseed\"}
                 {:person/id 2 :person/last-name \"Newton\"}])
  ```

  See [Update](https://coastonclojure.com/docs/queries.md#user-content-updates)"
  ([conn arg]
   (let [{:keys [adapter]} (db.connection/spec)
         v                 (helpers/update arg)]
     (condp = adapter
       "sqlite"   (if (nil? conn)
                    (transaction c
                                 (execute! c v)
                                 (let [table (if (sequential? arg)
                                               (-> arg first keys first namespace)
                                               (-> arg keys first namespace))
                                       id    (if (sequential? arg)
                                               (get-in arg [0 (keyword table "id")])
                                               (get arg (keyword table "id")))]
                                   (fetch c (keyword table) id)))
                    (execute! conn v))
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
  "Update a record if there's a conflict. Otherwise, insert it."
  ([conn arg opts]
   (let [table-name        (if (sequential? arg)
                             (-> arg first keys first utils/namespace*)
                             (-> arg keys first utils/namespace*))
         {:keys [adapter]} (db.connection/spec)]

     (when (nil? table-name)
       (throw (Exception. "coast/upsert expects a map with qualified keywords")))

     (condp = adapter
       "sqlite"   (if (nil? conn)
                    (transaction c
                                 (let [{sql :sql}  (pluck c ["select sql from sqlite_master where type = ? and name = ?" "table" table-name])
                                       on-conflict (if (list? (:on-conflict opts))
                                                     (:on-conflict opts)
                                                     (unique-column-names sql))
                                       v           (helpers/upsert arg {:on-conflict on-conflict})]
                                   (execute! c v)
                                   (let [{id :id} (pluck c ["select last_insert_rowid() as id"])
                                         id       (if (zero? id)
                                                    (get (find-by c (keyword table-name) (select-keys arg (mapv #(keyword table-name %) on-conflict)))
                                                         (keyword table-name "id"))
                                                    id)
                                         table    (if (sequential? arg)
                                                    (-> arg first keys first namespace)
                                                    (-> arg keys first namespace))]
                                     (fetch c (keyword table) id))))
                    (execute! conn (apply helpers/upsert arg opts)))
       "postgres" (if (nil? conn)
                    (transaction c
                                 (let [{indexdef :indexdef} (pluck c ["select indexdef from pg_indexes where tablename = ?" table-name])
                                       on-conflict          (-> (re-find #"\((.*)\)" indexdef)
                                                                (last)
                                                                (string/split #","))
                                       v                    (helpers/upsert arg {:on-conflict on-conflict})
                                       v                    (conj v :returning :*)]
                                   (q c v)))
                    (q conn (conj (helpers/upsert arg opts)
                                  :returning :*))))))
  ([arg]
   (upsert nil arg {})))


(defn delete
  "Delete only deletes rows by primary key `:id`

  Example:

  ```clojure
  (coast/delete {:person/id 1})

  See [Delete](https://coastonclojure.com/docs/queries.md#user-content-deletes)```"
  ([conn arg]
   (let [{:keys [adapter]} (db.connection/spec)
         v                 (helpers/delete arg)]
     (condp = adapter
       "sqlite"   (first
                    (execute! conn v))
       "postgres" (let [v (conj v :returning :*)]
                    (q conn v)))))
  ([arg]
   (delete nil arg)))


(defn pull
  "Pull syntax groups child records into vectors under a name you define.

  See [Pull](https://coastonclojure.com/docs/pull.md#user-content-pull)"
  [v ident]
  (pluck
    [:pull v
     :from (-> ident first namespace)
     :where ident]))


(defn any-rows?
  "Whether the table `table` is empty."
  [table]
  (some?
    (pluck
      [:select :*
       :from (keyword table)
       :limit 1])))


(def migrate migrations/migrate)
(def rollback migrations/rollback)
(def reconnect! db.connection/reconnect!)

(defn -main [& [action db-name]]
  (case action
    "create" (println (create (or db-name (spec :database))))
    "drop"   (println (drop (or db-name (spec :database))))
    "")
  (System/exit 0))
