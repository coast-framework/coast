(ns {{project}}.models.{{table}}
  (:require [coast.core :as coast]))

(defn all []
  (coast/query :{{table}}/all))

(defn find-by-id [id]
  (coast/query :{{table}}/find-by-id {:id id}))

(defn insert [m]
  (coast/query :{{table}}/insert m))

(defn update- [id m]
  (coast/query :{{table}}/update (merge {:id id} m)))

(defn delete [id]
  (coast/query :{{table}}/delete {:id id}))
