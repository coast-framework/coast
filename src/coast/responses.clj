(ns coast.responses
  (:require [hiccup.core :as h]
            [cheshire.core :as cheshire]))

(defn content-type [k]
  (condp = k
   :json {"Content-Type" "application/json"}
   :xml {"Content-Type" "text/xml"}
   {"Content-Type" "text/html"}))

(defn json? [response]
  (= "application/json" (get-in response [:headers "Content-Type"])))

(defn body [content-type bd]
  (condp = (get content-type "Content-Type")
    "text/html" (h/html bd)
    "application/json" (cheshire/generate-string bd)))

(defn response
  ([status val ct headers]
   (let [cont-type (content-type ct)
         b (body cont-type val)]
     {:status status
      :body b
      :headers (merge cont-type headers)}))
  ([status body ct]
   (response status body ct {}))
  ([status body]
   (response status body :html {})))

(defn redirect
  ([url flash]
   {:status 302
    :body ""
    :headers {"Location" url
              "Turbolinks-Location" url}
    :flash flash})
  ([url]
   (redirect url nil)))

(def ok (partial response 200))
(def bad-request (partial response 400))
(def unauthorized (partial response 401))
(def not-found (partial response 404))
(def forbidden (partial response 403))
(def internal-server-error (partial response 500))
