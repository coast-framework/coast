(ns coast.core
  (:require [potemkin]
            [org.httpkit.server]
            [trail.core])
  (:refer-clojure :exclude [get]))

(potemkin/import-vars
  [org.httpkit.server
   run-server]
  [trail.core
   defroutes
   get
   post
   put
   patch
   delete
   resource])
