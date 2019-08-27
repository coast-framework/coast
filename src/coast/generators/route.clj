(ns coast.generators.route
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [helper.core :as helper]
            [db.core :as db]
            [env.core :as env])
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


(defn pad-right [n]
  (format (str "%" n "s") ""))


(defn form-element [{:keys [table col value padding]}]
  (let [col-name (name col)
        table-name (name table)
        input-name (format "%s[%s]" table-name col-name)
        value-attr (if (some? value)
                     (format " :value (:%s %s)" col-name table-name)
                     "")]
    (string/join
      (str "\n" (pad-right (or padding 8)))
      [(format "(label {:for \"%s\"} \"%s\")" input-name col-name)
       (format "(text-field :%s :%s%s)" table-name col-name value-attr)
       (format "(field-error (-> errors :%s :%s))" table-name col-name)])))


(defn columns [table]
  (let [ctx (db/context (env/env :coast-env))
        {:keys [adapter]} ctx
        conn (db/connect ctx)]
    (cond
      (= adapter "sqlite") (db/query
                            conn
                            ["select p.name as column_name
                                   from sqlite_master m
                                   left outer join pragma_table_info((m.name)) p
                                         on m.name <> p.name
                                   where m.name = ?
                                   order by column_name" table])
      (= adapter "postgres") (db/query
                              conn
                              ["select column_name
                                      from information_schema.columns
                                      where table_schema not in ('pg_catalog', 'information_schema')
                                            and table_name = ?
                                      order by table_name, column_name" table])
      :else [])))

(defn cols! [table exclude?]
  (let [excluded-cols (if exclude?
                        #{"id" "updated-at" "created-at"}
                        #{})
        cols (columns (helper/sqlize table))
        cols (->> cols
                  (map :column-name)
                  (map helper/kebab-case)
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
  (let [col-name (name col)
        table-name (name table)]
    (string/join
      (str "\n" (pad-right 10))
      [(format "(dt \"%s\")" col-name)
       (format "(dd (:%s %s))" col-name table-name)])))


(defn table-headers [cols]
  (string/join (str "\n" (pad-right 16))
    (concat
      (map #(format "(th \"%s\")" (name %)) cols))))


(defn table-data [cols]
  (string/join (str "\n" (pad-right 18))
    (map #(format "(td (:%s row))" (name %)) cols)))


(defn write [table]
  (let [filename (str "src/routes/" table ".clj")
        template "generators/route.clj.txt"]
    (if (overwrite? filename)
      (let [cols (cols! table true)
            all-cols (cols! table false)]
        (->> (io/resource template)
             (slurp)
             (fill {:keywords (string/join " " cols)
                    :qualified-symbols (string/join " " (map symbol cols))
                    :form-elements (string/join (str "\n\n" (pad-right 8))
                                    (map #(form-element {:table table :col %}) cols))
                    :edit-elements (string/join (str "\n\n" (pad-right 10))
                                    (map #(form-element {:table table :col % :padding 10 :value true}) cols))
                    :data-elements (string/join (str "\n\n" (pad-right 10))
                                     (map #(dl-element table %) cols))
                    :table (helper/kebab-case table)
                    :table-headers (table-headers all-cols)
                    :table-data (table-data all-cols)})
             (spit! filename))
        (println filename "created successfully"))
      (println table "skipped"))))
