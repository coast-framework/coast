(ns coast.zeta
  (:require [coast.middleware :as middleware]
            [coast.router :as router]
            [coast.dev.server :as dev.server]
            [coast.prod.server :as prod.server]
            [coast.env :refer [env]]
            [coast.assets :as assets]
            [ring.middleware.defaults :as middleware.defaults]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]))

(defn resolve-routes
  "Eager require route namespaces when app is called for uberjar compat"
  [routes]
  (->> (map #(nth % 2) routes)
       (map #(if (vector? %) (first %) %))
       (map namespace)
       (distinct)
       (filter some?)
       (map symbol)
       (apply require)))

(defn prod? []
  (= "prod" (env :coast-env)))

(defn app
  "Tasteful ring middleware so you don't have to think about it"
  ([routes opts]
   ; hack for uberjar route resolution
   (resolve-routes routes)
   (let [layout (get opts :layout)
         not-found-page (get opts :404)
         error-page (get opts :500)
         bundles (assets/bundles (prod?) (:assets opts))]
     (-> (router/handler not-found-page)
         (middleware/wrap-layout layout)
         (middleware/wrap-with-logger)
         (middleware/wrap-route-middleware)
         (middleware/wrap-coerce-params)
         (router/wrap-route-info routes)
         (middleware/wrap-storage (get opts :storage))
         (middleware/wrap-bundles bundles)
         (wrap-content-type)
         (wrap-not-modified)
         (wrap-keyword-params {:keywordize? true :parse-namespaces? true})
         (middleware.defaults/wrap-defaults (middleware/coast-defaults opts))
         (middleware/wrap-not-found not-found-page)
         (middleware/wrap-errors error-page))))
  ([routes]
   (app routes {})))

(defn server
  "Runs http-kit server based on COAST_ENV env variable, options are COAST_ENV=dev or COAST_ENV=prod"
  ([app]
   (server app nil))
  ([app opts]
   (if (prod?)
     (prod.server/start app opts)
     (dev.server/restart app opts))))
