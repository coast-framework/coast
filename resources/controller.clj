(ns {{ns}}.controllers.{{table}}-controller
  (:require [coast.core :as coast]
            [{{ns}}.models.{{table}} :as {{table}}]
            [{{ns}}.views.{{table}} :as views.{{table}}]))

(defn index [request]
  (let [{{table}} ({{table}}/all)]
    (views.{{table}}/index (assoc request :{{table}} {{table}}))))

(defn show [request]
  (let [id (get-in request [:params :id])
        {{singular}} ({{table}}/find-by-id id)]
    (views.{{table}}/show (assoc request :{{singular}} {{singular}}))))

(defn fresh [request]
  (views.{{table}}/fresh request))

(defn create [request]
  (let [params (get request :params)
        [{{singular}} error] (coast/try! ({{table}}/insert params))]
    (if (nil? error)
      (coast/redirect "/{{table}}")
      (fresh (assoc request :error error)))))

(defn edit [request]
  (let [id (get-in request [:params :id])
        {{singular}} ({{table}}/find-by-id id)]
    (views.{{table}}/edit (assoc request :{{singular}} {{singular}}))))

(defn change [request]
  (let [params (get request :params)
        id (get params :id)
        [{{singular}} error] (coast/try! ({{table}}/update id params))]
    (if (nil? error)
      (coast/redirect "/{{table}}")
      (edit (assoc request :error error)))))

(defn delete [request]
  (let [id (get-in request [:params :id])
        [{{singular}} error] (coast/try! ({{table}}/delete id))]
    (coast/redirect "/" error)))
