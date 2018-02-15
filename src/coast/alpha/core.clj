(ns coast.alpha.core
  (:refer-clojure :exclude [get update list])
  (:require [bunyan.core :as bunyan]
            [coast.alpha.middleware :as middleware]
            [coast.alpha.router :as router]
            [coast.alpha.server :as server]
            [coast.alpha.db]
            [coast.alpha.responses]
            [coast.alpha.env]
            [coast.utils :as utils]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.reload :as reload]
            [prone.middleware :as prone]
            [hiccup.page]
            [potemkin]))

(defn app
  ([handler opts]
   (let [{:keys [layout error-fn not-found-fn]} opts]
     (-> handler
         (router/wrap-match-routes not-found-fn)
         (middleware/wrap-layout layout)
         (bunyan/wrap-with-logger)
         (defaults/wrap-defaults (middleware/coast-defaults opts))
         (middleware/wrap-not-found not-found-fn)
         (middleware/wrap-if utils/dev? reload/wrap-reload)
         (middleware/wrap-if utils/dev? prone/wrap-exceptions)
         (middleware/wrap-if utils/prod? middleware/wrap-errors error-fn))))
  ([handler]
   (app handler {})))

(def start server/start-server)

(potemkin/import-vars
  [coast.alpha.router
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
  [coast.alpha.db
   query
   query!]
  [coast.alpha.responses
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
   now
   uuid
   parse-int
   current-user
   printerr
   dev?
   test?
   prod?
   map-vals
   validate]
  [coast.alpha.env
   env])
