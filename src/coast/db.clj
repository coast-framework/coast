(ns coast.db
  (:require [db.core]
            [env.core :as env]
            [coast.generators]))


(defn columns [table columns]
  (fn [params-map]
    (let [filtered-map (select-keys (get params-map table) columns)]
      {table filtered-map})))


(defn -main [& [command]]
  (let [ctx (db.core/context
             (env/env :coast-env))]
    (case command
      "migrate" (db.core/migrate
                 (db.core/connect ctx))
      "rollback" (db.core/rollback
                  (db.core/context ctx))
      "create" (db.core/create ctx)
      "drop" (db.core/drop ctx)
      (coast.generators/usage))))
