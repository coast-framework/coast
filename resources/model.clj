(ns {{project}}.models.{{table}}
  (:require [coast.db :as db])
  (:refer-clojure :exclude [update]))

(def columns [{{columns}}])

(defn all []
  (db/query :{{table}}/all))

(defn find-by-id [id]
  (db/query :{{table}}/find-by-id {:id id}))

(defn insert [m]
  (->> (select-keys m columns)
       (db/insert :{{table}})))

(defn update [id m]
  (as-> (select-keys m columns) %
        (db/update :{{table}} % :{{table}}/where {:id id})))

(defn delete [id]
  (db/delete :{{table}} :{{table}}/where {:id id}))
