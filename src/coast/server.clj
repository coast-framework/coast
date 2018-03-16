(ns coast.server
  (:require [clojure.tools.namespace.repl :as repl]
            [org.httpkit.server :as httpkit]
            [coast.env :as env]
            [coast.utils :as utils]))

(defonce server (atom nil))

(defn port []
  (-> (or (env/env :port) "1337")
      (utils/parse-int)))

(defn options []
  {:port (port)})

(defn start
  ([app]
   (let [opts (options)]
     (println "Server is listening on port" (:port opts))
     (httpkit/run-server app opts)))
  ([]
   (reset! server (start (resolve (symbol "app"))))))

(defn stop []
  (when (not (nil? @server))
    (@server :timeout 100)
    (reset! server nil)
    (println "Resetting dev server")))

(defn restart [app]
  (def app app)
  (stop)
  (repl/refresh :after 'coast.server/start))
