(ns coast.db.associations
  (:require [db.associator]))


(def tables db.associator/tables)
(def table db.associator/table)
(def has-many db.associator/has-many)
(def belongs-to db.associator/belongs-to)
(def primary-key db.associator/primary-key)
