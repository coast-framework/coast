(ns coast.alpha.middleware
  (:require [coast.responses :as responses]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.session.cookie :as cookie]
            [environ.core :as environ]
            [coast.utils :as utils]
            [hiccup.page]
            [clojure.stacktrace :as st])
  (:import (clojure.lang ExceptionInfo)))

(defn internal-server-error []
  (responses/internal-server-error
    (hiccup.page/html5
      [:head
       [:title "Internal Server Error"]]
      [:body
       [:h1 "500 Internal server error"]])))

(defn wrap-errors [handler error-fn]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (println (st/print-stack-trace e))
        (or (internal-server-error)
            (responses/internal-server-error (error-fn)))))))

(defn wrap-not-found [handler not-found-page]
  (if (nil? not-found-page)
    (fn [request]
      (handler request))
    (fn [request]
      (try
        (handler request)
        (catch ExceptionInfo e
          (let [m (ex-data e)
                type (get m :coast/error-type)]
            (if (= type :not-found)
              (responses/not-found (not-found-page))
              (throw e))))))))

(defn layout? [response layout]
  (and (not (nil? layout))
       (or (vector? response)
           (string? response))))

(defn wrap-layout [handler layout]
  (fn [request]
    (let [response (handler request)]
      (cond
        (map? response) response
        (layout? response layout) (responses/ok (layout request response))
        :else (responses/ok response)))))

(defn wrap-if [handler pred wrapper & args]
  (if pred
    (apply wrapper handler args)
    handler))

(defn coast-defaults [opts]
  (let [secret (environ/env :secret)
        default-opts {:session {:cookie-name "id"
                                :store (cookie/cookie-store {:key secret})}}]
    (utils/deep-merge defaults/site-defaults default-opts opts)))
