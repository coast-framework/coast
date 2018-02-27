(ns coast.generators
  (:require [clojure.string :as string]
            [coast.db :as db]
            [coast.utils :as utils]
            [coast.words :as words]
            [clojure.java.io :as io]))

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

(defn sql [_ table]
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

(defn db [project table]
  (let [output (format "src/%s/db/%s.clj" project table)]
    (->> (io/resource "generators/db.clj")
         (slurp)
         (fill {:table (utils/kebab table)
                :project (utils/kebab project)})
         (spit output))
    (println (format "%s created successfully" output))))

(defn model [project table]
  (let [output (format "src/%s/models/%s.clj" project table)]
    (->> (io/resource "generators/model.clj")
         (slurp)
         (fill {:table (utils/kebab table)
                :project (utils/kebab project)
                :singular (-> table utils/kebab words/singular)
                :columns (->> (db/columns {:table-name table})
                              (map :column-name)
                              (map utils/kebab)
                              (string/join ", "))})
         (spit output))
    (println (format "%s created successfully" output))))

(defn controller [project table]
  (let [output (format "src/%s/controllers/%s.clj" project table)]
    (->> (io/resource "generators/controller.clj")
         (slurp)
         (fill {:table (utils/kebab table)
                :project (utils/kebab project)
                :singular (-> table utils/kebab words/singular)})
         (spit output))
    (println (format "%s created successfully" output))))

(defn view [project table]
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
        output (format "src/%s/views/%s.clj" project table)]
    (->> (io/resource "generators/view.clj")
         (slurp)
         (fill {:project (utils/kebab project)
                :table (utils/kebab table)
                :singular (words/singular table)
                :columns (string/join " " columns)
                :td-columns td-columns
                :th-columns th-columns
                :form-columns form-columns
                :div-columns div-columns})
         (spit output))
    (println (format "%s created successfully" output))))
