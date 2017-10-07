(ns coast.responses
  (:require [hiccup.core :as h]))

(defonce default-headers {"Content-Type" "text/html"})

(defn response [status body & {:as headers}]
  {:status status
   :body (h/html body)
   :headers (merge default-headers headers)})

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
