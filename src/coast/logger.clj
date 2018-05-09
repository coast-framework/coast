(ns coast.logger
  (:require [clojure.string :as string]
            [coast.time :as time])
  (:import (java.time Duration)))

(defn diff [start end]
  (let [duration (Duration/between start end)]
    (.toMillis duration)))

(defn req-method [request]
  (or (-> request :params :_method)
      (:request-method request)))

(defn log-str [request response start-time]
  (let [ms (diff start-time (time/now))
        uri (:uri request)
        status (:status response)
        method (-> (req-method request) name string/upper-case)
        content-type (get-in response [:headers "Content-Type"])
        route (:route/name request)]
    (str method " " uri " " route " " status " " content-type " " ms "ms")))

(defn log [request response start-time]
  (println (log-str request response start-time)))
