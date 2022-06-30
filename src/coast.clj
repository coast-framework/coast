(ns coast
  (:require [coast.potemkin.namespaces :as namespaces]
            [hiccup2.core]
            [coast.db]
            [coast.db.connection]
            [coast.theta]
            [coast.env]
            [coast.time2]
            [coast.components]
            [coast.responses]
            [coast.utils]
            [coast.error]
            [coast.router]
            [coast.validation])
  (:refer-clojure :exclude [update]))

(namespaces/import-vars
  [coast.responses
   ok
   bad-request
   no-content
   not-found
   unauthorized
   server-error
   redirect
   flash]

  [coast.error
   raise
   rescue]

  [coast.db
   q
   pull
   transact
   delete
   insert
   update
   first!
   pluck
   fetch
   execute!
   find-by
   transaction
   upsert
   any-rows?]

  [coast.db.connection
   connection]

  [coast.validation
   validate]

  [coast.components
   csrf
   form
   js
   css]

  [coast.router
   routes
   wrap-routes
   prefix-routes
   with
   with-prefix]

  [coast.middleware
   wrap-with-layout
   with-layout
   wrap-layout
   site-routes
   site
   api-routes
   api
   content-type?]

  [coast.theta
   server
   app
   url-for
   action-for
   redirect-to
   form-for]

  [coast.env
   env]

  [coast.utils
   uuid
   intern-var
   xhr?]

  [coast.time2
   now
   datetime
   instant
   strftime]

  [hiccup2.core
   raw
   html])
