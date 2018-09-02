(ns coast
  (:require [potemkin :refer [import-vars]]
            [coast.db]
            [coast.eta]
            [coast.components]
            [coast.responses]
            [coast.utils]
            [coast.error]
            [coast.router]
            [coast.validation]
            [coast.middleware]))

(import-vars
  [coast.responses
   ok
   not-found
   unauthorized
   internal-server-error
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
   first!]

  [coast.validation
   validate]

  [coast.components
   form
   js
   css]

  [coast.middleware
   wrap-layout]

  [coast.router
   action-for
   url-for]

  [coast.eta
   server
   app])
