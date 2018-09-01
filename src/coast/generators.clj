(ns coast.generators
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [coast.generators.action :as generators.action]
            [coast.migrations.sql :as migrations.sql]
            [coast.migrations.edn :as migrations.edn]))

(defn usage []
  (println "Usage:
  coast new <project-name>

Examples:
  coast new foo
  coast new another-foo

  coast gen migration <name>           # Creates a new edn migration file
  coast gen action <resource>          # Creates a five new clj files with view/action functions in src/<resource>/create/read/update/delete/list.clj
  coast gen action <resource>:<action> # Creates a new clj file with view/action functions in src/<resource>/<action>.clj"))

(defn gen [args]
  (let [[_ kind arg] args]
    (case kind
      "sql:migration" (migrations.sql/create arg)
      "migration" (migrations.edn/create arg)
      "action" (generators.action/write arg)
      "jobs" (->> (io/resource "migrations/create_jobs.sql")
                  (slurp)
                  (spit (str "resources/migrations/" (migrations.sql/filename "create-jobs"))))
      (usage))))

(defn -main [& args]
  (let [[action] args]
    (case action
      "gen" (gen args)
      (usage))))
