(ns coast.theta
  (:require [coast.middleware :as middleware]
            [coast.router :as router]
            [coast.dev.server :as dev.server]
            [coast.prod.server :as prod.server]
            [coast.env :as env]
            [coast.components]
            [coast.logger :as logger]))


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


(defn resolve-home
  "Eager require home routes for 404 and 500"
  []
  (try
    (require 'home)
    (catch Exception e)))


(defn app
  "The main entry point for coast apps"
  [opts]
  (let [routes (:routes opts)
        opts (dissoc opts :routes)
        opts (middleware/site-defaults opts)]

    ; eager require routes, components, middleware and default 404/500 pages
    (resolve-routes routes)
    (resolve-components)
    (resolve-middleware)
    (resolve-home)

    ; hack for url-for and action-for
    (def routes routes)

    (-> (router/handler routes)
        ; coast middleware
        (middleware/wrap-json-body)
        (middleware/wrap-coerce-params)
        (middleware/wrap-simulated-methods)
        (middleware/wrap-not-found routes)
        (middleware/wrap-site-errors routes)
        (middleware/wrap-json-response)
        (middleware/wrap-html-response)
        (middleware/wrap-plain-text-response)
        (middleware/wrap-logger (:logger opts))

        ; ring middleware
        (middleware/wrap middleware/wrap-keyword-params (get-in opts [:params :keywordize] false))
        (middleware/wrap middleware/wrap-nested-params (get-in opts [:params :nested] false))
        (middleware/wrap middleware/wrap-multipart-params (get-in opts [:params :multipart] false))
        (middleware/wrap middleware/wrap-params (get-in opts [:params :urlencoded] false))

        ; static file middleware
        (middleware/wrap middleware/wrap-absolute-redirects (get-in opts [:responses :absolute-redirects] false))
        (middleware/wrap-multi middleware/wrap-resource (get-in opts [:static :resources] false))
        (middleware/wrap-multi middleware/wrap-file (get-in opts [:static :files] false))
        (middleware/wrap middleware/wrap-content-type (get-in opts [:responses :content-types] false))
        (middleware/wrap middleware/wrap-default-charset (get-in opts [:responses :default-charset] false))
        (middleware/wrap middleware/wrap-not-modified (get-in opts [:responses :not-modified-responses] false))
        (middleware/wrap middleware/wrap-x-headers (:security opts))

        ; reload middleware
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
