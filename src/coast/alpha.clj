(ns coast.alpha
  (:refer-clojure :exclude [get update list])
  (:require [coast.alpha.middleware :as middleware]
            [coast.alpha.router :as router]
            [coast.alpha.server :as server]
            [coast.alpha.db]
            [coast.alpha.responses]
            [coast.alpha.env]
            [coast.alpha.components]
            [coast.alpha.time]
            [coast.alpha.utils :as utils]
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
         (middleware/wrap-if utils/dev? reload/wrap-reload)
         (middleware/wrap-if utils/dev? prone/wrap-exceptions)
         (middleware/wrap-if utils/prod? middleware/wrap-errors error-fn))))
  ([app-name handler]
   (app app-name handler {})))

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
   defq
   defq!]
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
  [coast.alpha.utils
   try+
   throw+
   uuid
   parse-int
   dev?
   test?
   prod?
   map-vals
   validate
   flip
   kebab
   snake]
  [coast.alpha.time
   now
   fmt]
  [coast.alpha.env
   env]
  [coast.alpha.components
   form-for
   link-to])
