(ns coast.delta
  (:require [coast.middleware :as middleware]
            [coast.router :as router]
            [ring.middleware.defaults :as middleware.defaults]))

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
         (middleware.defaults/wrap-defaults (middleware/coast-defaults opts))
         (middleware/wrap-not-found not-found-page)
         (middleware/wrap-errors error-page))))
  ([routes]
   (app routes {})))
