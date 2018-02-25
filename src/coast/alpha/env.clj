(ns coast.alpha.env
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [word.core :as word]))

(defn fmt [m]
  (->> (map (fn [[k v]] [(-> k .toLowerCase word/kebab keyword) v]) m)
       (into {})))

(defn dot-env []
  (let [file (io/file ".env")]
    (if (.exists file)
      (->> (slurp file)
           (string/split-lines)
           (map #(string/split % #"="))
           (into {}))
      {})))

(def env
  (-> (merge (dot-env) (System/getenv))
      (fmt)))
