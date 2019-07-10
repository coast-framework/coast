(ns coast.core
  (:require [coast.server :as server]))


(defn apps
  "Runs multiple coast router handlers until one of them matches a route"
  [& handlers]
  (fn [request]
    (some #(% request) handlers)))


(defn server
  "Starts the http server with the given app and any additional options"
  ([app]
   (server app {}))
  ([app opts]
   (server/restart app opts)))
