(ns coast.generators.migration
  (:require [coast.db]
            [clojure.set :as set]
            [coast.time :as time]
            [coast.utils :as utils]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import (java.io File)))

(defn timestamp []
  (-> (time/now)
      (time/fmt "yyyyMMddHHmmss")))

(defn column [s]
  (let [[col-name col-type] (string/split s #":")]
    (str "(" col-type " :" col-name ")")))

(defn clj-contents [ts mig-name args]
  (let [mig-command (re-find #"\w+-\w+" mig-name)
        [_ mig-table-name] (re-find #"\w+-\w+-(\w+)" mig-name)]
    (str "(ns migrations." ts "-" (string/replace mig-name "_" "-")
         "\n  (:require [coast.db.migrations :refer :all]))\n\n"
      (condp = mig-command
        "create-table" (str "(defn change []\n  (create-table :" mig-table-name
                            (if (not (empty? args))
                              (str "\n    " (string/join "\n    " (map column args)))
                              "")
                            "\n    (timestamps)))")
        "(defn change [])"))))

(defn contents [mig-type ts mig-name args]
  (condp = mig-type
    :sql (str "-- up\n\n-- down")
    :edn (str "[]")
    :clj (clj-contents ts mig-name args)
    ""))

(defn write [args]
  (when (and (some? args)
             (not (empty? args)))
    (let [migration-name (first args)
          ts (timestamp)
          file-type (last (string/split migration-name #"\."))
          migration-type (if (contains? #{"edn" "sql" "clj"} file-type)
                           file-type
                           "clj")
          migration-name (string/replace migration-name (str "." migration-type) "")
          filename (utils/sqlize
                    (str "db/migrations/" ts "-" migration-name "." migration-type))
          _ (io/make-parents filename)
          contents (contents (keyword migration-type) ts migration-name (rest args))]
      (spit filename contents)
      (println "Created migration" filename "successfully"))))
