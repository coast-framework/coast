(ns coast.eta
  (:require [coast.middleware :as middleware]
            [coast.router :as router]
            [coast.dev.server :as dev.server]
            [coast.prod.server :as prod.server]
            [coast.env :refer [env]]
            [clojure.java.io :as io]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]))

(defn resolve-routes
  "Eager require route namespaces when app is called for uberjar compat"
  [routes]
  (when (contains? #{"test" "prod"} (env :coast-env))
    (->> (map #(nth % 2) routes)
         (map #(if (vector? %) (first %) %))
         (map namespace)
         (distinct)
         (filter some?)
         (map symbol)
         (apply require))))

(defn resolve-components
  "Eager require components"
  [opts]
  (when (and (contains? #{"test" "prod"} (env :coast-env))
             (not= :api (:wrap-defaults opts)))
    (require 'components)))

(defn app
  "Tasteful ring middleware so you don't have to think about it"
  ([routes opts]
   ; hack for url-for and action-for
   (def routes routes)
   ; hack for uberjar route resolution
   (resolve-routes routes)
   (resolve-components opts)
   (let [layout (get opts :layout (resolve `components/layout))
         not-found-page (get opts :404 (resolve `error.not-found/view))
         error-page (get opts :500 (resolve `error.internal-server-error/view))]
     (-> (router/handler not-found-page)
         (middleware/wrap-layout layout)
         (middleware/wrap-with-logger)
         (middleware/wrap-storage (get opts :storage))
         (middleware/wrap-coerce-params)
         (wrap-keyword-params {:keywordize? true :parse-namespaces? true})
         (middleware/wrap-defaults opts)
         (middleware/wrap-json-params opts)
         (middleware/wrap-json-response opts)
         (middleware/wrap-route-middleware)
         (router/wrap-route-info routes)
         (middleware/wrap-not-found not-found-page)
         (middleware/wrap-errors error-page))))
  ([routes]
   (app routes {})))

(defn server
  "Runs an http-kit server based on the COAST_ENV env variable, options are dev, test or prod"
  ([app]
   (server app nil))
  ([app opts]
   (if (= "prod" (env :coast-env))
     (prod.server/start app opts)
     (dev.server/restart app opts))))

(defn url-for [k]
  ((router/url-for-routes routes) k))

(defn action-for [k]
  ((router/action-for-routes routes) k))
