(ns coast.dev.server
  (:require [coast.repl :as repl]
            [org.httpkit.server :as httpkit]
            [coast.env :as env]
            [coast.utils :as utils]
            [ring.middleware.reload :as reload]
            [coast.dev.middleware :as dev.middleware]))

(def server (atom nil))

(defn start
  ([app]
   (start app nil))
  ([app opts]
   (let [port (-> (or (:port opts) (env/env :port) 1337)
                  (utils/parse-int))]
     (def app app)
     (reset! server (httpkit/run-server (dev.middleware/wrap-exceptions
                                         (reload/wrap-reload #'app)) (merge opts {:port port})))
     (println "Server is listening on port" port))))

(defn stop []
  (when (not (nil? @server))
    (@server :timeout 100)
    (reset! server nil)
    (println "Resetting dev server")))

(defn restart
  "Here's the magic that allows you to restart the server at will from the repl. It uses a custom version of repl/refresh that takes arguments"
  [app opts]
  (stop)
  (repl/refresh :after `start :after-args [app opts]))
