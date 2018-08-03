(ns coast.prod.server
  (:require [org.httpkit.server :as httpkit]
            [coast.env :as env]
            [coast.utils :as utils]))

(defn start
  "The only difference between the prod server and the dev server is the lack of an atom for restarts"
  ([app opts]
   (let [port (-> (or (:port opts) (env/env :port) 1337)
                  (utils/parse-int))]
     (println "Server is listening on port" port)
     (httpkit/run-server app {:port port})))
  ([app]
   (start app nil)))
