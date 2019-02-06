(ns coast.generators
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [coast.generators.action :as generators.action]
            [coast.generators.migration :as generators.migration]))


(defn usage []
  (println "Usage:
  coast new <project-name>

Examples:
  coast new foo
  coast new another-foo

  coast gen migration <name>           # Creates a new migration file
  coast gen migration <name>.sql       # Creates a new plain old sql migration file
  coast gen action <resource>          # Creates a five new clj files with view/action functions in src/<resource>/create/read/update/delete/list.clj
  coast gen action <resource>:<action> # Creates a new clj file with view/action functions in src/<resource>/<action>.clj"))


(defn gen [args]
  (let [[_ kind arg] args]
    (case kind
      "migration" (generators.migration/write (drop 2 args))
      "action" (generators.action/write arg)
      (usage))))


(defn -main [& args]
  (let [[action] args]
    (case action
      "gen" (gen args)
      (usage))))
