(ns coast.controllers
  (:require [coast.responses]))

(defmacro try+ [f error-fn]
  `(try
     ~f
    (catch clojure.lang.ExceptionInfo e#
      (let [ex# (ex-data e#)]
        (~error-fn (ex-data e#))))))

(def ok coast.responses/ok)
(def redirect coast.responses/redirect)
(def flash coast.responses/flash)
