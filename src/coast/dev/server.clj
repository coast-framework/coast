(ns coast.dev.server
  (:require [clojure.tools.namespace.repl :as repl]
            [org.httpkit.server :as httpkit]
            [coast.env :as env]
            [coast.utils :as utils]))

(def server (atom nil))

(defn start []
  (let [opts (->> (or (env/env :port) "1337")
                  (utils/parse-int)
                  (hash-map :port))]
    (println "Server is listening on port" (:port opts))
    (reset! server (httpkit/run-server (resolve (symbol "app")) opts))))

(defn stop []
  (when (not (nil? @server))
    (@server :timeout 100)
    (reset! server nil)
    (println "Resetting dev server")))

(defn restart [app]
  (def app app)
  (stop)
  (repl/refresh :after `start))
