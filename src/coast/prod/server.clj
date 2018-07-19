(ns coast.prod.server
  (:require [org.httpkit.server :as httpkit]
            [coast.env :as env]
            [coast.utils :as utils]))

(defn start
  ([app opts]
   (let [port (-> (or (:port opts) (env/env :port) 1337)
                  (utils/parse-int))]
     (println "Server is listening on port" port)
     (httpkit/run-server app {:port port})))
  ([app]
   (start app nil)))
