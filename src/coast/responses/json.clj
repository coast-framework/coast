(ns coast.responses.json
  (:require [clojure.data.json :as json]))

(defn response
  ([status body headers]
   {:status status
    :body (json/write-str body)
    :headers (merge {"Content-Type" "application/json"} headers)})
  ([status body]
   (response status body {})))

(def ok (partial response 200))
(def created (partial response 201))
(def accepted (partial response 202))
(def no-content (partial response 204))
(def bad-request (partial response 400))
(def unauthorized (partial response 401))
(def not-found (partial response 404))
(def forbidden (partial response 403))
(def internal-server-error (partial response 500))
