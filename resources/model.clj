(ns {{project}}.models.{{table}}
  (:require [coast.db :as db]))

(defn all []
  (db/query :{{table}}/all))

(defn find-by-id [id]
  (db/query :{{table}}/find-by-id {:id id}))

(defn insert [m]
  (db/insert :{{table}} m))

(defn update- [id m]
  (db/update :{{table}} m :{{table}}/where {:id id}))

(defn delete [id]
  (db/delete :{{table}} :{{table}}/where {:id id}))
