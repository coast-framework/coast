(ns coast.generators
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [coast.generators.code :as generators.code]
            [coast.generators.migration :as generators.migration]))


(defn usage []
  (println "Usage:
  coast new <project-name>

Examples:
  coast new foo
  coast new another-foo

  coast gen migration <name>           # Creates a new migration file
  coast gen migration <name>.sql       # Creates a new plain old sql migration file
  coast gen code <table>               # Creates a new clj file with handler functions in src/<table>.clj"))


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
      (usage))))
