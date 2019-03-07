(ns coast.db.migrations
  (:require [coast.utils :as utils]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [coast.db.connection :refer [spec]]))

(def rollback? (atom false))
(def vectors (atom []))

(def sql {"sqlite" {:timestamp "timestamp"
                    :now "current_timestamp"
                    :pk "integer primary key"}
          "postgres" {:timestamp "timestamptz"
                      :now "now()"
                      :pk "serial primary key"}})


(defn not-null [m]
  (when (false? (:null m))
    "not null"))


(defn col-default [m]
  (when (contains? m :default)
    (str "default " (get m :default))))


(defn unique [m]
  (when (true? (:unique m))
    (str "unique")))


(defn collate [m]
  (when (contains? m :collate)
    (str "collate " (get m :collate))))


(defn col-type [type {:keys [precision scale]}]
  (if (or (some? precision)
          (some? scale))
    (str (or (utils/sqlize type) "numeric")
         (when (or (some? precision)
                   (some? scale))
           (str "(" (string/join ","
                      (filter some? [(or precision 0) scale]))
                ")")))
    (utils/sqlize type)))


(defn on-delete [m]
  (when (contains? m :on-delete)
    (str "on delete " (utils/sqlize (:on-delete m)))))


(defn on-update [m]
  (when (contains? m :on-update)
    (str "on update " (utils/sqlize (:on-update m)))))


(defn reference [m]
  (when (contains? m :references)
    (str "references " (:references m))))


(defn col [type col-name m]
  "SQL fragment for adding a column in create or alter table"
  (swap! vectors conj [:col type col-name (or m {})])
  (->> [(utils/sqlize col-name)
        (col-type type m)
        (unique m)
        (collate m)
        (not-null m)
        (col-default m)
        (reference m)
        (on-delete m)
        (on-update m)]
       (filter some?)
       (string/join " ")
       (string/trim)))


(defn references [col-name & {:as m}]
  (col :integer col-name (merge {:null false :references (str (utils/sqlize col-name) "(id)") :index true :on-delete "cascade"} m)))


(defn drop-column
  "SQL for dropping a column from a table"
  [table col]
  (str "alter table " (utils/sqlize table) " drop column " (utils/sqlize col)))


(defn add-column
  "SQL for adding a column to an existing table"
  [table col-name type & {:as m}]
  (if (true? @rollback?)
    (drop-column table col-name)
    (str "alter table " (utils/sqlize table) " add column " (col type col-name m))))


(defn add-foreign-key
  "SQL for adding a foreign key column to an existing table"
  [from to & {col :col pk :pk fk-name :name :as m}]
  (let [from (utils/sqlize from)
        to (utils/sqlize to)]
   (string/join " "
     (filter some?
       ["alter table"
        from
        "add constraint"
        (or (utils/sqlize fk-name) (str from "_" to "_fk"))
        "foreign key"
        (str "(" (or (utils/sqlize col) to) ")")
        "references"
        to
        (str "(" (or (utils/sqlize pk) "id") ")")
        (on-delete m)
        (on-update m)]))))


(defn where [m]
  (when (contains? m :where)
    (str "where " (:where m))))


(defn index-cols [cols {order :order}]
  (->> (map #(conj [%] (get order %)) cols)
       (map #(map utils/sqlize %))
       (map #(string/join " " %))
       (map string/trim)))


(defn add-index
  "SQL for adding an index to an existing table"
  [table-name cols & {:as m}]
  (let [table-name (utils/sqlize table-name)
        cols (if (sequential? cols)
               cols
               [cols])
        cols (index-cols cols m)
        col-name (string/join ", " cols)
        index-col-names (map #(string/replace % #" " "_") cols)
        index-name (or (:name m) (str table-name "_" (string/join "_" index-col-names) "_index"))]
    (string/join " "
      (filter some?
        ["create"
         (unique m)
         "index"
         index-name
         "on"
         table-name
         (str "(" col-name ")")
         (where m)]))))


(defn add-reference
  "SQL for adding a foreign key column to an existing table"
  [table-name ref-name & {:as m}]
  (string/join " "
    (filter some?
      ["alter table"
       (utils/sqlize table-name)
       "add column"
       (utils/sqlize ref-name)
       (or (-> m :type utils/sqlize) "integer")
       "references"
       (utils/sqlize ref-name)
       (str "(id)")])))


(defn alter-column [table-name col-name type & {:as m}]
  (string/join " "
    (filter some?
      ["alter table"
       (utils/sqlize table-name)
       "alter column"
       (utils/sqlize col-name)
       "type"
       (utils/sqlize type)
       (when (contains? m :using)
        (str "using " (:using m)))])))


(defn text [col-name & {:as m}]
  (col :text col-name m))


(defn timestamp [col-name & {:as m}]
  (col :timestamp col-name m))


(defn datetime [col-name & {:as m}]
  (col :datetime col-name m))


(defn timestamptz [col-name & {:as m}]
  (col :timestamptz col-name m))


(defn integer [col-name & {:as m}]
  (col :integer col-name m))


(defn bool [col-name & {:as m}]
  (col :boolean col-name m))


(defn decimal [col-name & {:as m}]
  (col :decimal col-name m))


(defn drop-table [table]
  (str "drop table " (utils/sqlize table)))


(defn has-index? [v]
  (true? (get (last v) :index)))


(defn create-table
  "SQL to create a table"
  [table & args]
  (if (true? @rollback?)
    (drop-table table)
    (let [args (if (sequential? args) args '())
          index-vectors (filter has-index? @vectors)
          index-sql-strings (map #(add-index table (nth % 2)) index-vectors)]
      (concat
        [(string/join " "
           (filter some?
             [(str "create table " (utils/sqlize table) " (")
              (string/join ", "
               (conj args (str "id " (get-in sql [(spec :adapter) :pk]))))
              ")"]))]
        index-sql-strings))))


(defn create-extension [s]
  (str "create extension " s))


(defn drop-extension [s]
  (str "drop extension " s))


(defn drop-column
  "SQL for dropping a column from a table"
  [table col]
  (str "alter table " (utils/sqlize table) " drop column " (utils/sqlize col)))


(defn drop-foreign-key [alter-table-name & {:as m}]
  (let [constraint (when (contains? m :table)
                     (utils/sqlize (:table m)) "_" (utils/sqlize alter-table-name) "_fkey")
        constraint (if (contains? m :name)
                     (utils/sqlize (:name m))
                     constraint)]
    (str "alter table " (utils/sqlize alter-table-name) " drop constraint " constraint)))


(defn drop-index [table-name & {cols :column :as m}]
  (let [cols (if (sequential? cols) cols [cols])
        cols (index-cols cols m)
        col-name (string/join ", " cols)
        index-col-names (map #(string/replace % #" " "_") cols)
        index-name (or (-> m :name utils/sqlize) (str table-name "_" (string/join "_" index-col-names) "_index"))]
    (str "drop index " index-name)))


(defn drop-reference [table-name ref-name]
  (str "alter table "
       (utils/sqlize table-name)
       " drop constraint "
       (utils/sqlize ref-name) "_" (utils/sqlize table-name) "_fkey"))


(defn rename-column [table-name column-name new-column-name]
  (string/join " "
    ["alter table"
     (utils/sqlize table-name)
     "rename column"
     (utils/sqlize column-name)
     "to"
     (utils/sqlize new-column-name)]))


(defn rename-index [index-name new-index-name]
  (string/join " "
    ["alter index"
     index-name
     "rename to"
     new-index-name]))


(defn rename-table [table-name new-table-name]
  (string/join " "
    ["alter table"
     table-name
     "rename to"
     new-table-name]))


(defn timestamps []
  (string/join " "
    [(str "updated_at " (get-in sql [(spec :adapter) :timestamp]) ",")
     (str "created_at " (get-in sql [(spec :adapter) :timestamp]) " not null default " (get-in sql [(spec :adapter) :now]))]))
