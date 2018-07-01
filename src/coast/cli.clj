(ns coast.cli
  (:require [coast.db :as db]
            [coast.utils :as utils]
            [coast.migrations.sql :as migrations.sql]
            [coast.migrations.edn :as migrations.edn]))

(defn -main [& args]
  (let [[action db-name] args
        db-name (utils/snake db-name)]
    (case action
      "db:create" (db/create db-name)
      "db:drop" (db/drop db-name)
      "db:migrate" (do (migrations.sql/migrate)
                       (migrations.edn/migrate))
      "db:rollback" (migrations.sql/rollback)
      "")))
