(ns coast.alpha
  (:refer-clojure :exclude [get update list])
  (:require [coast.middleware :as middleware]
            [coast.router :as router]
            [coast.server :as server]
            [coast.db]
            [coast.responses]
            [coast.env]
            [coast.components]
            [coast.time]
            [coast.utils]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.reload :as reload]
            [prone.middleware :as prone]
            [hiccup.page]
            [potemkin]))

(defn app
  ([app-name routes opts]
   (let [{:keys [layout error-fn not-found-fn]} opts]
     (-> (router/match-routes app-name routes not-found-fn)
         (middleware/wrap-layout layout)
         (middleware/wrap-with-logger)
         (defaults/wrap-defaults (middleware/coast-defaults opts))
         (middleware/wrap-not-found not-found-fn)
         (middleware/wrap-if #(= "dev" (coast.env/env :coast-env)) reload/wrap-reload)
         (middleware/wrap-if #(= "dev" (coast.env/env :coast-env)) prone/wrap-exceptions)
         (middleware/wrap-if #(= "prod" (coast.env/env :coast-env)) middleware/wrap-errors error-fn))))
  ([app-name handler]
   (app app-name handler {})))

(def start server/start-server)

(potemkin/import-vars
  [coast.router
   get
   post
   put
   patch
   delete
   resource
   wrap-routes-with
   match-routes
   url
   action]
  [coast.db
   defq
   defq!]
  [coast.responses
   ok
   bad-request
   unauthorized
   not-found
   forbidden
   internal-server-error
   redirect
   flash]
  [hiccup.page
   html5
   include-js
   include-css]
  [coast.utils
   try+
   throw+
   uuid
   parse-int
   map-vals
   validate
   kebab
   snake]
  [coast.time
   now
   fmt]
  [coast.env
   env]
  [coast.components
   form-for
   link-to])
