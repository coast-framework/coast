(ns coast.error
  (:require [coast.db.errors :as db.errors]))

(defn raise
  ([s m]
   (throw (ex-info s (assoc m ::raise true))))
  ([m]
   (raise "Error has occurred" m)))

(defmacro rescue [f]
  `(try
    [~f nil]
    (catch org.postgresql.util.PSQLException e#
      (let [error-map# (db.errors/error-map e#)]
        (if (empty? error-map#)
          (throw e#)
          [nil error-map#])))
    (catch clojure.lang.ExceptionInfo e#
      (let [ex# (ex-data e#)]
        (if (contains? ex# ::raise)
          [nil ex#]
          (throw e#))))))
