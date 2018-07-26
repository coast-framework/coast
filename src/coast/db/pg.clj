(ns coast.db.pg
  (:require [clojure.data.json :as json]
            [clojure.string :as string])
  (:import [org.postgresql.util PGobject]))

(defn qualify-col [s]
  (let [parts (string/split s #"\$")
        k-ns (first (map #(string/replace % #"_" "-") parts))
        k-n (->> (rest parts)
                 (map #(string/replace % #"_" "-"))
                 (string/join "-"))]
    (keyword k-ns k-n)))

(defn read-pg-vector
  "oidvector, int2vector, etc. are space separated lists"
  [s]
  (when (seq s) (clojure.string/split s #"\s+")))

(defn read-pg-array
  "Arrays are of form {1,2,3}"
  [s]
  (when (seq s) (when-let [[_ content] (re-matches #"^\{(.+)\}$" s)] (if-not (empty? content) (clojure.string/split content #"\s*,\s*") []))))

(defmulti read-pgobject
  "Convert returned PGobject to Clojure value."
  #(keyword (when % (.getType ^org.postgresql.util.PGobject %))))

(defmethod read-pgobject :oidvector
  [^org.postgresql.util.PGobject x]
  (when-let [val (.getValue x)]
    (mapv read-string (read-pg-vector val))))

(defmethod read-pgobject :int2vector
  [^org.postgresql.util.PGobject x]
  (when-let [val (.getValue x)]
    (mapv read-string (read-pg-vector val))))

(defmethod read-pgobject :anyarray
  [^org.postgresql.util.PGobject x]
  (when-let [val (.getValue x)]
    (vec (read-pg-array val))))

(defmethod read-pgobject :json
  [^org.postgresql.util.PGobject x]
  (when-let [val (.getValue x)]
    (json/read-str val
                   :key-fn qualify-col)))

(defmethod read-pgobject :jsonb
  [^org.postgresql.util.PGobject x]
  (when-let [val (.getValue x)]
    (json/read-str val
                   :key-fn qualify-col)))

(defmethod read-pgobject :default
  [^org.postgresql.util.PGobject x]
  (.getValue x))
