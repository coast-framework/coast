(ns coast.generators
  (:require [coast.generators.route :as generators.route]
            [db.core :as db]
            [env.core :as env]))


(defn usage []
  (println "Usage:
  coast new <project-name>
  coast gen migration <name>
  coast gen route <table>
  coast db <migrate|rollback|create|drop>

Examples:
  coast new foo
  coast new another-foo

  coast gen migration create-table-todo     # Creates a new migration file
  coast gen migration create-table-todo.sql # Creates a new sql migration file

  coast gen route todo                      # Creates a new route clojure file with handler functions in src/routes/todo.clj

  coast db migrate                          # runs all migrations found in db/migrations
  coast db rollback                         # rolls back the latest migration
  coast db create                           # creates a new database defined in db.edn
  coast db drop                             # drops a database defined in db.edn"))


(defn db [command]
  (let [ctx (db/context
             (env/env :coast-env))]
    (case command
      "migrate" (println
                 (db/migrate
                  (db/connect ctx)))
      "rollback" (println
                  (db/rollback
                   (db/context ctx)))
      "create" (println
                (db/create ctx))
      "drop" (println
              (db/drop ctx))
      (usage))))


(defn gen [args]
  (let [[_ kind arg] args]
    (case kind
      "migration" (db/migration (drop 2 args))
      "route" (generators.route/write arg)
      (usage))))


(defn -main [& args]
  (let [[action command] args]
    (case action
      "gen" (gen args)
      "db" (db command)
      (usage))
    (System/exit 0)))
