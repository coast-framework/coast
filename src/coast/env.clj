(ns coast.env
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [coast.utils :as utils]))

(defn fmt
  "This formats .env keys that LOOK_LIKE_THIS to keys that :look-like-this"
  [m]
  (->> (map (fn [[k v]] [(-> k .toLowerCase (utils/kebab) keyword) v]) m)
       (into {})))

(defn dot-env
  "Environment variables all come from .env, specify it on prod, specify it on dev, live a happy life"
  []
  (let [file (io/file ".env")]
    (if (.exists file)
      (->> (slurp file)
           (string/split-lines)
           (map string/trim)
           (filter #(not (string/blank? %)))
           (map #(string/split % #"="))
           (map #(mapv (fn [s] (string/trim s)) %))
           (into {}))
      {})))

(defn env-edn
  "Environment variables can also come from the easily-parse-able env.edn"
  []
  (let [file (io/file "env.edn")]
    (if (.exists file)
      (-> file slurp edn/read-string)
      {})))

(defn env
  "This formats and merges environment variables from .env, env.edn and the OS environment"
  [k]
  (let [m (fmt (merge (dot-env) (System/getenv)))
        m (merge (env-edn) m)]
    (get m k)))
