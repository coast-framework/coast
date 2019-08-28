(ns coast.server
  (:require [helper.core :as helper]
            [env.core :as env]
            [org.httpkit.server :as httpkit]))

(defonce server (atom nil))

(defn start
  "Starts an http server"
  ([app opts]
   (let [port (helper/parse-int (or (:port opts) (env/env :port) 1337))]
     (reset! server (httpkit/run-server app (merge opts {:port port})))
     (println "HTTP server is listening on port" port)))
  ([app]
   (start app nil)))


(defn stop []
  (when (not (nil? @server))
    (@server :timeout 100)
    (reset! server nil)
    (println "Resetting server")))


(defn restart
  "Restarts the server"
  ([app opts]
   (stop)
   (start app opts))
  ([app]
   (restart app {})))
