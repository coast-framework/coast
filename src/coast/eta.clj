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
       (map symbol)
       (apply require)))

(defn resolve-components
  "Eager require components"
  []
  (try
    (require 'components)
    (catch Exception e)))

(defn app
  "The main entry point for coast apps"
  [opts]
  (let [{:keys [routes routes/site routes/api]} opts
        api (wrap-routes utils/api-route? api)
        routes (or routes (concat site api))]

    ; url-for and action-for hack
    (def routes routes)

    ; eager load hacks
    (resolve-components)
    (resolve-routes routes)
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

(defn url-for
  "Creates a url from a route name"
  ([k m]
   (if-let [anchor (:# m)]
     (str ((url-for-routes routes) k (dissoc m :#)) "#" (name anchor))
     ((url-for-routes routes) k m)))
  ([k]
   (url-for k {})))

(defn action-for
  ([k m]
   ((action-for-routes routes) k m))
  ([k]
   (action-for k {})))
