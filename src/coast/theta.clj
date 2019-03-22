(ns coast.theta
  (:require [coast.middleware :as middleware]
            [coast.router :as router]
            [coast.dev.server :as dev.server]
            [coast.prod.server :as prod.server]
            [coast.env :as env]
            [coast.components]))


(defn route-handler [v]
  (let [handler (if (= 2 (count v))
                  (second v)
                  (nth v 2))]
    (if (vector? handler)
      (or (first handler) (nth v 1))
      handler)))


(defn resolve-routes
  "Eager require route namespaces when app is called for uberjar compat"
  [routes]
  (->> (map route-handler routes)
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


(defn resolve-middleware
  "Eager require components"
  []
  (try
    (require 'middleware)
    (catch Exception e)))



(defn app
  "The main entry point for coast apps"
  [opts]
  (let [routes (:routes opts)
        opts (dissoc opts :routes)]

    ; eager require routes and components
    (resolve-routes routes)
    (resolve-components)
    (resolve-middleware)

    ; hack for url-for and action-for
    (def routes routes)

    (-> (router/handler routes opts)
        (middleware/wrap-logger)
        (middleware/wrap-file opts)
        (middleware/wrap-absolute-redirects)
        (middleware/wrap-resource "public")
        (middleware/wrap-content-type)
        (middleware/wrap-plain-text-content-type)
        (middleware/wrap-json-response-with-content-type)
        (middleware/wrap-default-charset "utf-8")
        (middleware/wrap-not-modified)
        (middleware/wrap-simulated-methods)
        (middleware/wrap-coerce-params)
        (middleware/wrap-keyword-params)
        (middleware/wrap-params)
        (middleware/wrap-not-found routes)
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
    (coast.components/form (merge (action-for k m) opts)
      body)))
