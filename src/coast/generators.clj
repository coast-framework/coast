(ns coast.generators
  (:require [coast.generators.code :as generators.code]
            [coast.generators.migration :as generators.migration]
            [coast.migrations :as migrations]
            [coast.db :as db]))


(defn usage []
  (println "Usage:
  coast new <project-name>
  coast gen migration <name>
  coast gen code <table>
  coast db <migrate|rollback>

Examples:
  coast new foo
  coast new another-foo

  coast gen migration create-table-todo     # Creates a new migration file
  coast gen sql:migration create-table-todo # Creates a new sql migration file

  coast gen code todo                       # Creates a new clj file with handler functions in src/todo.clj

  coast db migrate                          # runs all migrations found in db/migrations
  coast db rollback                         # rolls back the latest migration"))


(defn gen [args]
  (let [[_ kind arg] args]
    (case kind
      "migration" (generators.migration/write (drop 2 args))
      "code" (generators.code/write arg)
      (usage))))


(defn -main [& args]
  (let [[action] args]
    (case action
      "gen" (gen args)
      "db" (cond
             (contains? #{"migrate" "rollback"} (second args)) (migrations/-main (second args))
             (contains? #{"create" "drop"} (second args)) (db/-main (second args))
             :else (usage))
      (usage))))
