(ns coast.migrations.edn
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.edn]
            [coast.db.schema :as schema]
            [coast.time :as time])
  (:import (java.io File)))

(defn migrate [content]
  (let [tables (schema/create-tables-if-not-exists content)
        cols (schema/add-columns content)
        idents (schema/add-idents content)
        rels (schema/add-rels content)
        constraints (schema/add-constraints content)]
    (->> (concat tables cols idents rels constraints)
         (filter some?)
         (string/join ";\n"))))

(defn rollback [content]
  (let [;TODO tables (schema/drop-table content)
        cols (schema/drop-columns content)
        constraints (schema/drop-constraints content)]
    (->> (concat constraints cols)
         (filter some?)
         (string/join ";\n"))))
