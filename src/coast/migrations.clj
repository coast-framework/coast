(ns coast.migrations
  (:require [trek.core :as trek]
            [coast.db :as db]))

(defn migrate []
  (trek/migrate (db/connection)))

(defn rollback []
  (trek/rollback (db/connection)))

(def create trek/create)
