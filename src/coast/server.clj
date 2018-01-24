(ns coast.server
  (:require [clojure.tools.namespace.repl :as repl]
            [org.httpkit.server :as httpkit]
            [environ.core :as environ]
            [coast.utils :as utils]))

(defonce server-atom (atom nil))

(defn parse-port [opts]
  (-> (or (get opts :port)
          (environ/env :port)
          1337)
      (utils/parse-int)))

(defn start
  ([app opts]
   (let [port (parse-port opts)]
     (println "Server is listening on port" port)
     (httpkit/run-server app {:port port})))
  ([app]
   (start app {}))
  ([]
   (reset! server-atom (start (resolve (symbol "app"))))))

(defn stop []
  (when @server-atom
    (@server-atom :timeout 100)
    (reset! server-atom nil)))

(defn restart []
  (stop)
  (repl/refresh :after 'coast.server/start))

(defn reload-server [app]
  (def app app)
  (restart))

(defn start-server
  ([app opts]
   (start app opts))
  ([app]
   (reload-server app)))
