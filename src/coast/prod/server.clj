(ns coast.prod.server
  (:require [org.httpkit.server :as httpkit]
            [coast.env :as env]
            [coast.utils :as utils]))

(defn start
  "The prod server doesn't handle restarts with an atom, it's built for speed"
  ([app opts]
   (let [port (-> (or (:port opts) (env/env :port) 1337)
                  (utils/parse-int))]
     (println "Server is listening on port" port)
     (httpkit/run-server app (merge opts {:port port}))))
  ([app]
   (start app nil)))
