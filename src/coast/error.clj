(ns coast.error
  (:require [coast.db.errors :as db.errors]))

(defn raise
  ([s m]
   (throw (ex-info s (assoc m ::raise true))))
  ([m]
   (raise "Error has occurred" m)))

(defmacro rescue
  "Regular exceptions leave little to be desired. raise and rescue are wrappers around ExceptionInfo"
  ([f k]
   `(try
     [~f nil]
     (catch org.postgresql.util.PSQLException e#
       (let [error-map# (db.errors/error-map e#)]
         (if (empty? error-map#)
           (throw e#)
           [nil error-map#])))
     (catch clojure.lang.ExceptionInfo e#
       (let [ex# (ex-data e#)]
         (if (and (contains? ex# ::raise)
                  (contains? ex# (or ~k ::raise)))
           [nil ex#]
           (throw e#))))))
  ([f]
   `(rescue ~f nil)))
