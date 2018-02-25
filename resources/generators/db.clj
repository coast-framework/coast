(ns __project.db.__table
  (:require [coast.alpha.db :refer [defq]])
  (:refer-clojure :exclude [update list find]))

(defq list "resources/sql/__table.model.sql")
(defq find "resources/sql/__table.model.sql")
(defq insert "resources/sql/__table.model.sql")
(defq update "resources/sql/__table.model.sql")
(defq delete "resources/sql/__table.model.sql")
