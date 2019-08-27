(ns coast.db
  (:require [coast.generators]))


(defn -main [& [command]]
  (coast.generators/db command)
  (System/exit 0))
