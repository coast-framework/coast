(ns coast.generators
  (:require [selmer.parser :as selmer]
            [inflections.core :as inflections]
            [coast.db :as db]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:import (java.io File)))

(defn form-col? [s]
  (and (not= s "id")
       (not= s "created_at")
       (not (clojure.string/ends-with? s "_id"))))

(defn path [& parts]
  (string/join "/" parts))

(defn overwrite? [filename]
  (if (.exists (io/file filename))
    (do
      (println filename "already exists. Overwrite? y/n")
      (let [input (-> (read-line)
                      (.toLowerCase))]
        (= input "y")))
    true))

(defn sql [project table]
  (let [params {:project project
                :table (string/replace table #"-" "_")}
        dir (path "resources" "sql")
        filename (path dir (str table ".sql"))
        _ (.mkdirs (File. dir))]
    (if (overwrite? filename)
      (do
        (spit filename (selmer/render-file "crud.sql" params))
        (println table "sql generated"))
      (println table "sql skipped"))))

(defn model [project table]
  (let [params {:project project
                :ns (string/replace project #"_" "-")
                :table (string/replace table #"_" "-")
                :columns (->> (db/get-cols table)
                              (map :column_name)
                              (filter form-col?)
                              (map #(str ":" %))
                              (string/join " "))}
        dir (path "src" project "models")
        filename (path dir  (str table ".clj"))
        _ (.mkdirs (File. dir))]
    (sql project table)
    (if (overwrite? filename)
      (do
        (spit filename (selmer/render-file "model.clj" params))
        (println table "model generated"))
      (println table "model skipped"))))

(defn controller [project table]
  (let [params {:project project
                :ns (string/replace project #"_" "-")
                :table (string/replace table #"_" "-")
                :singular (inflections/singular table)}
        dir (path "src" project "controllers")
        filename (path dir (str table "_controller.clj"))
        _ (.mkdirs (File. dir))]
    (if (overwrite? filename)
      (do
        (spit filename (selmer/render-file "controller.clj" params))
        (println table "controller generated"))
      (println table "controller skipped"))))

(defn view [project table]
  (let [cols (->> (db/get-cols table)
                  (map :column_name))
        form-cols (filter form-col? cols)
        params {:project project
                :ns (string/replace project #"_" "-")
                :table (string/replace table #"_" "-")
                :singular (inflections/singular table)
                :columns cols
                :form_columns form-cols
                :column_string (string/join " " cols)
                :form_column_string (string/join " " form-cols)}
        dir (str "src/" project "/views")
        filename (str dir "/" table ".clj")
        _ (.mkdirs (File. dir))]
    (if (overwrite? filename)
      (do
        (spit filename (selmer/render-file "view.clj" params))
        (println table "view generated"))
      (println table "view skipped"))))

(defn mvc [project table]
  (do
    (model project table)
    (view project table)
    (controller project table)))
