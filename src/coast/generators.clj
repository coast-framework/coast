(ns coast.generators
  (:require [clojure.string :as string]
            [coast.db :as db]
            [coast.utils :as utils]
            [coast.words :as words]
            [clojure.java.io :as io]
            [coast.migrations :as migrations]))

(def pattern #"__([\w-]+)")

(defn replacement [match m]
  (let [default (first match)
        k (-> match last keyword)]
    (str (get m k default))))

(defn fill [m s]
  (string/replace s pattern #(replacement % m)))

(def excluded-col-set #{"id" "created_at"})

(defn excluded-cols [s]
  (not (contains? excluded-col-set s)))

(defn sql [table]
  (let [cols (->> (db/columns {:table-name table})
                  (map :column-name)
                  (filter excluded-cols))
        insert-columns (string/join ",\n  " cols)
        insert-values (->> (map #(str ":" %) cols)
                           (string/join ",\n  "))
        update-columns (->> (map #(format "%s = %s" % (str ":" %)) cols)
                            (string/join ",\n  "))
        output-filename (format "resources/sql/%s.db.sql" table)]
    (->> (io/resource "generators/db.sql")
         (slurp)
         (fill {:table table
                :insert-columns insert-columns
                :insert-values insert-values
                :update-columns update-columns})
         (spit output-filename))
    (println (format "%s created successfully" output-filename))))

(defn db [table]
  (let [output (format "src/db/%s.clj" table)]
    (->> (io/resource "generators/db.clj")
         (slurp)
         (fill {:table (utils/kebab table)})
         (spit output))
    (println (format "%s created successfully" output))))

(defn model [table]
  (let [output (format "src/models/%s.clj" table)
        _ (sql table)
        _ (db table)]
    (->> (io/resource "generators/model.clj")
         (slurp)
         (fill {:table (utils/kebab table)
                :singular (-> table utils/kebab words/singular)
                :columns (->> (db/columns {:table-name table})
                              (map :column-name)
                              (map utils/kebab)
                              (string/join ", "))})
         (spit output))
    (println (format "%s created successfully" output))))

(defn controller [table]
  (let [output (format "src/controllers/%s.clj" table)]
    (->> (io/resource "generators/controller.clj")
         (slurp)
         (fill {:table (utils/kebab table)
                :singular (-> table utils/kebab words/singular)})
         (spit output))
    (println (format "%s created successfully" output))))

(defn view [table]
  (let [columns (->> (db/columns {:table-name table})
                     (map :column-name)
                     (filter excluded-cols)
                     (map utils/kebab))
        th-columns (->> (map #(str "[:th \"" % "\"]") columns)
                        (string/join "\n            "))
        td-columns (->> (map #(str "[:td " % "]") columns)
                        (string/join "\n      "))
        div-columns (->> (map #(str "[:div " % "]") columns)
                         (string/join "\n      "))
        form-columns (->> (map #(format "[:div\n         [:label \"%s\"]\n         [:input {:type \"text\" :name  \"%s\" :value %s}]]" % % %) columns)
                          (string/join "\n       "))
        output (format "src/views/%s.clj" table)]
    (->> (io/resource "generators/view.clj")
         (slurp)
         (fill {:table (utils/kebab table)
                :singular (words/singular table)
                :columns (string/join " " columns)
                :td-columns td-columns
                :th-columns th-columns
                :form-columns form-columns
                :div-columns div-columns})
         (spit output))
    (println (format "%s created successfully" output))))

(defn usage []
  (println "Usage:
  coast new <project-name>

Examples:
  coast new foo
  coast new another-foo

  coast gen migration                                          # Creates a new migration
  coast gen migration create-<table> column1:text column2:text # Creates a new migration with a create table statement along with columns/types specified
  coast gen sql <table>                                        # Creates a new db.sql file in resources/sql with default sql queries/statement
  coast gen db <table>                                         # Creates a new db.clj file in src/db with default database functions
  coast gen model <table>                                      # Creates a new model.clj file in src/models
  coast gen controller <table>                                 # Creates a new controller.clj file in src/controllers
  coast gen view <table>                                       # Creates a new view.clj file in src/views"))

(defn gen [args]
  (let [[_ kind table] args]
    (case kind
      "migration" (apply migrations/create (drop 2 args))
      "sql" (sql table)
      "db" (db table)
      "model" (do
                (sql table)
                (db table)
                (model table))
      "controller" (controller table)
      "view" (view table)
      "mvc" (do
              (sql table)
              (db table)
              (model table)
              (controller table)
              (view table))
      (usage))))

(defn -main [& args]
  (let [[action] args]
    (case action
      "gen" (gen args)
      (usage))))
