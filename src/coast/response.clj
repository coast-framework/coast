(ns coast.response
  (:require [hiccup2.core :as hiccup2]))


(defn content-type [k]
  (let [content-types {:html "text/html"
                       :json "application/json"
                       :text "text/plain"
                       :xml "text/xml"}]
    (get content-types k "application/octet-stream")))


(defn flash [response s]
  (assoc response :flash s))


(defn redirect [url]
  {:status 302
   :body ""
   :headers {"Location" url}})


(defn render [ct body & {:as options}]
  (let [{:keys [status]} options
        headers (merge {"Content-Type" (content-type ct)}
                 (dissoc options :status))]
    {:status (or status 200)
     :headers headers
     :body body}))


(defmacro html [& args]
  `(str (hiccup2/html ~@args)))
