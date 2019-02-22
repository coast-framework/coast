(ns coast.theta
  (:require [coast.middleware :as middleware]
            [coast.router :as router]
            [coast.dev.server :as dev.server]
            [coast.prod.server :as prod.server]
            [coast.env :as env]
            [coast.components :as components]))

(defn app
  "The main entry point for coast apps"
  [opts]
  (let [routes (:routes opts)
        opts (dissoc opts :routes)]
    ; hack for url-for and action-for
    (def routes routes)
    (-> (router/handler routes opts)
        (middleware/wrap-logger)
        (middleware/wrap-file opts)
        (middleware/wrap-absolute-redirects)
        (middleware/wrap-resource "public")
        (middleware/wrap-content-type)
        (middleware/wrap-default-charset "utf-8")
        (middleware/wrap-not-modified)
        (middleware/wrap-simulated-methods)
        (middleware/wrap-coerce-params)
        (middleware/wrap-keyword-params)
        (middleware/wrap-params)
        (middleware/wrap-site-errors routes)
        (middleware/wrap-reload))))


(defn server
  "Runs an http-kit server based on the COAST_ENV env variable, options are dev, test or prod"
  ([app]
   (server app nil))
  ([app opts]
   (if (= "dev" (env/env :coast-env))
     (dev.server/restart app opts)
     (prod.server/start app opts))))


(defn url-for
  "Creates a url from a route name"
  ([k m]
   (if-let [anchor (:# m)]
     (str ((router/url-for-routes routes) k (dissoc m :#)) "#" (name anchor))
     ((router/url-for-routes routes) k m)))
  ([k]
   (url-for k {})))


(defn action-for
  ([k m]
   ((router/action-for-routes routes) k m))
  ([k]
   (action-for k {})))


(defn redirect-to [& args]
  {:status 302
   :body ""
   :headers {"Location" (apply url-for args)}})


(defn form-for [k & body]
  (let [m (if (map? (first body))
            (first body)
            {})
        body (if (map? (first body))
               (rest body)
               body)
        opts (when (map? (first body))
               (first body))
        body (if (map? (first body))
               (drop 1 body)
               body)]
    (components/form (merge (action-for k m) opts)
      body)))
