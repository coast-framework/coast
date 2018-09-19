(ns coast.eta
  (:require [coast.middleware :refer [wrap-logger wrap-file]]
            [coast.middleware.site :refer [wrap-site-defaults]]
            [coast.middleware.api :refer [wrap-api-defaults]]
            [coast.router :refer [wrap-routes url-for-routes action-for-routes handler wrap-middleware wrap-route-info]]
            [coast.dev.server :as dev.server]
            [coast.prod.server :as prod.server]
            [coast.env :refer [env]]
            [coast.utils :as utils]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn resolve-routes
  "Eager require route namespaces when app is called for uberjar compat"
  [routes]
  (->> (map #(nth % 2) routes)
       (map #(if (vector? %) (first %) %))
       (map namespace)
       (distinct)
       (filter some?)
       (map symbol)))

(defn resolve-components
  "Eager require components"
  []
  (when (contains? #{"test" "prod"} (env :coast-env))
    (require 'components)))

(defn app
  "The main entry point for all coast websites"
  [opts]
  (let [{:keys [routes routes/site routes/api]} opts
        api (wrap-routes utils/api-route? api)
        routes (or routes (concat site api))]
    ; url-for and action-for hack
    (def routes routes)
    ; uberjar eager load hack
    (resolve-routes routes)
    (resolve-components)
    (-> (handler opts)
        (wrap-middleware)
        (wrap-file opts)
        (wrap-api-defaults opts)
        (wrap-site-defaults opts)
        (wrap-logger)
        (wrap-route-info routes))))

(defn server
  "Runs an http-kit server based on the COAST_ENV env variable, options are dev, test or prod"
  ([app]
   (server app nil))
  ([app opts]
   (if (= "dev" (env :coast-env))
     (dev.server/restart app opts)
     (prod.server/start app opts))))

(defn url-for [k]
  ((url-for-routes routes) k))

(defn action-for [k]
  ((action-for-routes routes) k))
