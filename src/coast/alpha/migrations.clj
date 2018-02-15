(ns coast.alpha.migrations
  (:require [coast.alpha.migrations.core :as migrations.core]
            [coast.db :as db]))

(defn migrate []
  (migrations.core/migrate (db/connection)))

(defn rollback []
  (migrations.core/rollback (db/connection)))

(def create migrations.core/create)
