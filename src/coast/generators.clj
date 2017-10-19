(ns coast.generators
  (:require [selmer.parser :as selmer]
            [inflections.core :as inflections]
            [coast.db :as db]
            [clojure.string :as string])
  (:import (java.io File)))

(defn model [project table]
  (let [params {:project project
                :ns (string/replace project #"_" "-")
                :table (string/replace table #"_" "-")}
        dir (str "src/" project "/models")
        filename (str dir "/" table ".clj")
        _ (.mkdirs (File. dir))]
    (spit filename (selmer/render-file "model.clj" params))
    (println (str table " model generated"))))

(defn controller [project table]
  (let [params {:project project
                :ns (string/replace project #"_" "-")
                :table (string/replace table #"_" "-")
                :singular (inflections/singular table)}
        dir (str "src/" project "/controllers")
        filename (str dir "/" table "_controller.clj")
        _ (.mkdirs (File. dir))]
    (spit filename (selmer/render-file "controller.clj" params))
    (println (str table " controller generated"))))

(defn form-col? [s]
  (and (not= s "id")
       (not= s "created_at")
       (not (clojure.string/ends-with? s "_id"))))

(defn view [project table]
  (let [cols (->> (db/get-cols table)
                  (map :column_name))
        params {:project project
                :ns (string/replace project #"_" "-")
                :table (string/replace table #"_" "-")
                :singular (inflections/singular table)
                :columns cols
                :form_columns (filter form-col? cols)}
        dir (str "src/" project "/views")
        filename (str dir "/" table ".clj")
        _ (.mkdirs (File. dir))]
    (spit filename (selmer/render-file "view.clj" params))
    (println (str table " view generated"))))

(defn mvc [project table]
  (do
    (model project table)
    (view project table)
    (controller project table)))
