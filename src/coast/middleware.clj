(ns coast.middleware
  (:require [clojure.string :as string]
            [clojure.edn :as edn]
            [coast.time :as time]
            [coast.logger :as logger])
  (:import (clojure.lang ExceptionInfo)
           (java.time Duration)))

(defn wrap-logger [handler]
  (fn [request]
    (let [now (time/now)
          response (handler request)]
      (logger/log request response now)
      response)))
