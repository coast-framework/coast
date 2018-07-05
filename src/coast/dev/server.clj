(ns coast.dev.server
  (:require [coast.repl :as repl]
            [org.httpkit.server :as httpkit]
            [coast.env :as env]
            [coast.utils :as utils]))

(def server (atom nil))

(defn start
  ([app]
   (start app nil))
  ([app opts]
   (let [port (-> (or (:port opts) (env/env :port) 1337)
                  (utils/parse-int))]
     (println "Server is listening on port" port)
     (reset! server (httpkit/run-server app {:port port})))))

(defn stop []
  (when (not (nil? @server))
    (@server :timeout 100)
    (reset! server nil)
    (println "Resetting dev server")))

(defn restart [app opts]
  (stop)
  (repl/refresh :after `start :after-args [app opts]))
