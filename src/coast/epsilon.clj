(ns coast.epsilon
  (:require [coast.middleware :as middleware]
            [coast.router :as router]
            [coast.dev.server :as dev.server]
            [coast.prod.server :as prod.server]
            [coast.env :refer [env]]
            [ring.middleware.defaults :as middleware.defaults]
            [ring.middleware.keyword-params]))

(defn app
  ([routes opts]
   (let [layout (get opts :layout)
         not-found-page (get opts :404)
         error-page (get opts :500)]
     (-> (router/handler not-found-page)
         (middleware/wrap-layout layout)
         (middleware/wrap-with-logger)
         (middleware/wrap-route-middleware)
         (middleware/wrap-coerce-params)
         (router/wrap-route-info routes)
         (ring.middleware.keyword-params/wrap-keyword-params {:keywordize? true
                                                              :parse-namespaces? true})
         (middleware.defaults/wrap-defaults (middleware/coast-defaults opts))
         (middleware/wrap-not-found not-found-page)
         (middleware/wrap-errors error-page)
         (coast.middleware/wrap-reload))))
  ([routes]
   (app routes {})))

(defn server
  ([app]
   (server app nil))
  ([app opts]
   (if (= "prod" (env :coast-env))
     (prod.server/start app opts)
     (dev.server/restart app opts))))
