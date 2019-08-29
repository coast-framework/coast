(ns coast.responses)

(def content-type-headers {:html {"content-type" "text/html"}
                           :json {"content-type" "application/json"}})

(defn response
  ([status body headers]
   (let [m {:status status
            :body body
            :headers headers}]
     (if (contains? #{:html :json} headers)
       (assoc m :headers (get content-type-headers headers))
       m)))
  ([status body]
   (response status body {})))

(defn flash
  "Inject a string `s` that will be persistent after the redirect."
  [response s]
  (assoc response :flash s))

(defn redirect
  "Return a Ring response map with status code 302 and url `url`"
  [url]
  {:status  302
   :body    ""
   :headers {"Location" url}})

(def ok
  "Return a Ring response map with status code 200"
  (partial response 200))

(def created
  "Return a Ring response map with status code 201"
  (partial response 201))

(def accepted
  "Return a Ring response map with status code 202"
  (partial response 202))

(def no-content
  "Return a Ring response map with status code 204"
  (partial response 204))

(def bad-request
  "Return a Ring response map with status code 400"
  (partial response 400))

(def unauthorized
  "Return a Ring response map with status code 401"
  (partial response 401))

(def not-found
  "Return a Ring response map with status code 404"
  (partial response 404))

(def forbidden
  "Return a Ring response map with status code 403"
  (partial response 403))

(def server-error
  "Return a Ring response map with status code 500"
  (partial response 500))
