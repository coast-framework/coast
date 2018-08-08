(ns coast.cli
  (:require [coast.db :as db]
            [coast.utils :as utils]
            [coast.migrations :as migrations]))

(defn -main [& args]
  (let [[action db-name] args
        db-name (utils/snake db-name)]
    (case action
      "db:create" (db/create db-name)
      "db:drop" (db/drop db-name)
      "db:migrate" (migrations/migrate)
      "db:rollback" (migrations/rollback)
      "")))
