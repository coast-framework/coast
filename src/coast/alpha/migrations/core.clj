(ns coast.alpha.migrations.core
  (:require [clojure.java.jdbc :as sql]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [coast.alpha.migrations.parser :as parser]
            [coast.utils :as utils])
  (:import (java.io File)
           (java.time.format DateTimeFormatter)
           (java.time LocalDateTime)))

(def empty-migration "-- up\n\n-- down")

(defn migrations-dir []
  (.mkdirs (File. "resources/migrations"))
  "resources/migrations")

(defn completed-migrations [db]
  (let [sql "create table if not exists schema_migrations (id text, created_at timestamp without time zone default (now() at time zone 'utc'))"
        _ (sql/execute! db sql)]
    (->> (sql/query db ["select id from schema_migrations order by created_at"])
         (map :id))))

(defn migration-files []
  (->> (migrations-dir)
       (io/file)
       (file-seq)
       (filter #(.isFile %))
       (map #(.getName %))
       (filter #(.endsWith % ".sql"))))

(defn pending [db]
  (let [all (set (migration-files))
        completed (set (completed-migrations db))]
    (sort (vec (clojure.set/difference all completed)))))

(defn parse [s name]
  (when (not (nil? name))
    (-> (str (migrations-dir) "/" name)
        (slurp)
        (parser/parse)
        (get s))))

(defn migrate-all [db]
  (sql/with-db-connection [conn db]
                          (let [pending-migrations (pending conn)]
                            (doseq [migration pending-migrations]
                              (sql/execute! conn (parse "up" migration))
                              (sql/execute! conn ["insert into schema_migrations (id) values (?)" migration])
                              (println (str (string/replace migration #"\.sql" "") " migrated successfully"))))))

(defn print-result [type error]
  (println (str "-- " (string/capitalize type) " failed --------------------"))
  (println "")
  (println error))

(defn migrate [db]
  (let [[_ error] (->> (migrate-all db)
                       (utils/try!))]
    (when (some? error)
      (print-result "migrate" error))))

(defn execute! [db sql]
  (when (not (nil? sql))
    (sql/execute! db sql)))

(defn remove-migration [db migration]
  (sql/execute! db ["delete from schema_migrations where id = ?" migration])
  (println (str (string/replace migration #"\.sql" "") " rolled back successfully")))

(defn rollback [db]
  (sql/with-db-connection [conn db]
                          (let [migration (last (completed-migrations conn))
                                [result error] (->> (parse "down" migration)
                                                    (execute! conn)
                                                    (utils/try!))]
                            (if (or (some? result)
                                    (some? error))
                              (remove-migration conn migration)
                              (print-result "rollback" error)))))

(defn fmt-date [date]
  (when (instance? LocalDateTime date)
    (let [formatter (DateTimeFormatter/ofPattern "yyyyMMddHHmmss")]
      (.format formatter date))))

(defn timestamp []
  (-> (utils/now)
      (fmt-date)))

(defn filename [name]
  (when (and
          ((comp not nil?) name)
          ((comp not empty?) name)
          (string? name))
    (str (timestamp) "_" (string/replace name #"\s+|-+|_+" "_") ".sql")))

(defn column [arg]
  (let [[name type] (string/split arg #":")]
    (str name " " type)))

(defn parse-table-name [s]
  (->> (string/split s #"[-_]")
       (drop 1)
       (string/join "_")))

(defn create-table-contents [name args]
  (let [table (parse-table-name name)
        sql (str "create table " table " (\n")
        columns (map column args)
        columns (-> columns
                    (conj "  id serial primary key")
                    vec
                    (conj "created_at timestamp without time zone default (now() at time zone 'utc')"))
        column-string (string/join ",\n  " columns)]
    (str sql column-string "\n)")))

(defn drop-table-contents [s]
  (str "drop table " (parse-table-name s)))

(defn add-column-contents [s args]
  (let [table (->> (re-seq #"add-columns-to-([\w-_]+)" s)
                   (first)
                   (last))
        columns (map column args)]
    (->> (map #(str "alter table " table  " add column " %) columns)
         (string/join ";\n"))))

(defn add-column-contents-down [s args]
  (let [table (->> (re-seq #"add-columns-to-([\w-_]+)" s)
                   (first)
                   (last))
        columns (map column args)]
    (->> (map #(str "alter table " table  " drop " %) columns)
         (string/join ";\n"))))

(defn drop-column-contents [s args]
  (let [table (->> (re-seq #"drop-columns-from-([\w-_]+)" s)
                   (first)
                   (last))
        columns (map column args)]
    (->> (map #(str "alter table " table  " drop " %) columns)
         (string/join ";\n"))))

(defn drop-column-contents-down [s args]
  (let [table (->> (re-seq #"drop-columns-from-([\w-_]+)" s)
                   (first)
                   (last))
        columns (map column args)]
    (->> (map #(str "alter table " table  " add column " %) columns)
         (string/join ";\n"))))

(defn contents [s args]
  (cond
    (string/starts-with? s "create-") (str "-- up\n" (create-table-contents s args) "\n\n"
                                           "-- down\n" (drop-table-contents s))
    (string/starts-with? s "drop-") (str "-- up\n\n"
                                         "-- down\n" (drop-table-contents s))
    (string/starts-with? s "add-columns-to-") (str "-- up\n" (add-column-contents s args) "\n\n"
                                                   "-- down\n" (add-column-contents-down s args))
    (string/starts-with? s "drop-columns-from-") (str "-- up\n" (drop-column-contents s args) "\n\n"
                                                      "-- down\n" (drop-column-contents-down s args))
    :else empty-migration))

(defn create [name & args]
  (let [migration (filename name)
        dir (migrations-dir)]
    (spit (str dir "/" migration) (contents name args))
    (println (str dir "/" migration " created"))
    migration))
