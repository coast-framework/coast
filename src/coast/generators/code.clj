(ns coast.generators.code
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [coast.utils :as utils :refer [kebab snake]]
            [coast.db.connection :refer [connection spec]]
            [clojure.java.jdbc :as jdbc])
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


(defn form-element [k]
  (str "[:div
     [:label {:for \"" (str (namespace k) "/" (name k)) "\"} \""(name k) "\"]
     [:input {:type \"text\" :name \"" (str (namespace k) "/" (name k)) "\" :value (-> request :params "(str k)")}]]"))


(defn edit-element [k]
  (str "[:div
       [:label {:for \"" (str (namespace k) "/" (name k)) "\"} \""(name k) "\"]
       [:input {:type \"text\" :name \"" (str (namespace k) "/" (name k)) "\" :value ("(str k) " " (namespace  k)")}]]"))


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


(defn cols! [resource]
  (let [excluded-cols #{"id" "updated-at" "created-at"}
        cols (columns (snake resource))
        cols (->> cols
                  (map :column_name)
                  (map kebab)
                  (set))
        cols (set/difference cols excluded-cols)]
    (map #(keyword resource %) cols)))


(defn spit! [f s]
  (let [dir (->> (string/split f #"/")
                 (drop-last)
                 (string/join "/"))]
    (.mkdirs (File. dir))

    (spit f s)))


(defn dl-element [k]
  (str "[:dl
       [:dt \""(name k)"\"]
       [:dd (" (str k) " " (namespace k) ")]]"))


(defn table-html [ks]
  (str "[:table
     [:thead
      [:tr
       " (string/join "\n       "
          (map #(str "[:th \"" (name %) "\"]" ) ks)) "
       [:th]
       [:th]
       [:th]]]
     [:tbody
       (for [row rows]
        [:tr
         " (string/join "\n         "
             (map #(str "[:td (" (str %) " row)]") ks)) "
         [:td
          [:a {:href (coast/url-for ::view row)} \"View\"]]
         [:td
          [:a {:href (coast/url-for ::edit row)} \"Edit\"]]
         [:td
          [:a {:href (coast/url-for ::delete row)} \"Delete\"]]])]]"))

(defn write [table]
  (let [filename (str "src/" table ".clj")
        template "generators/code.clj.txt"]
    (if (overwrite? filename)
      (let [cols (cols! table)]
        (->> (io/resource template)
             (slurp)
             (fill {:index-pull-symbols (string/join " " (conj
                                                          (map utils/keyword->symbol cols)
                                                          (symbol table "id")))
                    :qualified-keywords (string/join " " cols)
                    :qualified-symbols (string/join " " (map utils/keyword->symbol cols))
                    :change-keywords (string/join " " (conj cols (keyword table "id")))
                    :form-elements (string/join "\n\n    "
                                    (map form-element cols))
                    :edit-elements (string/join "\n\n      "
                                    (map edit-element cols))
                    :dl-elements (string/join "\n\n     "
                                   (map dl-element cols))
                    :table (kebab table)
                    :table-html (table-html cols)})
             (spit! filename))
        (println filename "created successfully"))
      (println table "skipped"))))
