(ns coast.generators.code
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [coast.db.connection :refer [connection spec]]
            [coast.utils :as utils])
  (:import (java.io File)))


(def pattern #"__([\w-]+)__")


(defn replacement [match m]
  (let [s (first match)
        k (-> match last keyword)]
    (str (get m k s))))


(defn fill [m s]
  (string/replace s pattern #(replacement % m)))


(defn prompt [s]
  (print s)
  (flush)
  (read-line))


(defn overwrite? [filename]
  (if (.exists (io/file filename))
    (= (prompt (str filename " already exists. Overwrite? [y/n] ")) "y")
    true))


(defn form-element [table col]
  (str "(label {:for \"" (str table "[" (name col)) "]\"} \""(name col) "\")\n        (text-field :" table " :" (name col) ")"))


(defn edit-element [table col]
  (str "(label {:for \"" (str table "[" (name col)) "]\"} \""(name col) "\")\n          (text-field :" table " :" (name col) " :value (:" (name col) " " table "))"))


(defn columns [table]
  (cond
    (= (spec :adapter) "sqlite") (jdbc/query
                                  (connection)
                                  ["select p.name as column_name
                                   from sqlite_master m
                                   left outer join pragma_table_info((m.name)) p
                                         on m.name <> p.name
                                   where m.name = ?
                                   order by column_name" table])
    (= (spec :adapter) "postgres") (jdbc/query
                                    (connection)
                                    ["select column_name
                                      from information_schema.columns
                                      where table_schema not in ('pg_catalog', 'information_schema')
                                            and table_name = ?
                                      order by table_name, column_name" table])
    :else []))

(defn cols! [table exclude?]
  (let [excluded-cols (if exclude?
                        #{"id" "updated-at" "created-at"}
                        #{})
        cols (columns (utils/sqlize table))
        cols (->> cols
                  (map :column_name)
                  (map utils/kebab-case)
                  (set))
        cols (set/difference cols excluded-cols)]
    (map keyword cols)))


(defn spit! [f s]
  (let [dir (->> (string/split f #"/")
                 (drop-last)
                 (string/join "/"))]
    (.mkdirs (File. dir))

    (spit f s)))


(defn dl-element [table col]
  (str "(dt \"" (name col) "\")\n          (dd (" (str col) " " table "))"))


(defn table-headers [cols]
  (string/join "\n                "
    (map #(str "(th \"" (name %) "\")") cols)))


(defn table-data [cols]
  (string/join "\n                  "
    (map #(str "(td (" (str %) " row))") cols)))


(defn write [table]
  (let [filename (str "src/routes/" table ".clj")
        template "generators/code.clj.txt"]
    (if (overwrite? filename)
      (let [cols (cols! table true)
            all-cols (cols! table false)]
        (->> (io/resource template)
             (slurp)
             (fill {:keywords (string/join " " cols)
                    :qualified-symbols (string/join " " (map utils/keyword->symbol cols))
                    :form-elements (string/join "\n\n        "
                                    (map #(form-element table %) cols))
                    :edit-elements (string/join "\n\n          "
                                    (map #(edit-element table %) cols))
                    :data-elements (string/join "\n\n        "
                                     (map #(dl-element table %) cols))
                    :table (utils/kebab-case table)
                    :table-headers (table-headers all-cols)
                    :table-data (table-data all-cols)})
             (spit! filename))
        (println filename "created successfully"))
      (println table "skipped"))))
