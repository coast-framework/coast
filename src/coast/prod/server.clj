(ns coast.prod.server
  (:require [org.httpkit.server :as httpkit]
            [coast.env :as env]
            [coast.utils :as utils]))

(defn start
  ([app port]
   (let [opts (-> (or port (env/env :port) "1337")
                  (utils/parse-int)
                  (hash-map :port))]
     (println "Server is listening on port" (:port opts))
     (httpkit/run-server app opts)))
  ([app]
   (start app nil)))
