(ns coast.responses)

(defn response
  ([status body headers]
   {:status status
    :body body
    :headers headers})
  ([status body]
   (response status body {})))

(defn flash [response s]
  (assoc response :flash s))

(defn redirect [url]
  {:status 302
   :body ""
   :headers {"Location" url}})

(def ok (partial response 200))
(def created (partial response 201))
(def accepted (partial response 202))
(def no-content (partial response 204))
(def bad-request (partial response 400))
(def unauthorized (partial response 401))
(def not-found (partial response 404))
(def forbidden (partial response 403))
(def internal-server-error (partial response 500))
