(ns coast.gamma
  (:require [coast.middleware :as middleware]
            [coast.router :as router]
            [coast.env :as env]
            [ring.middleware.defaults :as middleware.defaults]
            [coast.server :as server]))

(defn app
  ([routes opts]
   (let [{:keys [layout]} opts
         not-found-fn (get opts :404)
         error-fn (get opts :500)]
     (-> (router/match-routes routes not-found-fn)
         (middleware/wrap-layout layout)
         (middleware/wrap-with-logger)
         (middleware.defaults/wrap-defaults (middleware/coast-defaults opts))
         (middleware/wrap-not-found not-found-fn)
         (middleware/wrap-reload)
         (middleware/wrap-if #(= "prod" (env/env :coast-env)) middleware/wrap-errors error-fn))))
  ([routes]
   (app routes {})))

(def start-server server/start)
(def stop-server server/stop)
(def restart-server server/restart)
