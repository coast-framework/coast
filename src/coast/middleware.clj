(ns coast.middleware
  (:require [ring.middleware.defaults :as defaults]
            [ring.middleware.session.cookie :as cookie]
            [clojure.stacktrace :as st]
            [clojure.string :as string]
            [coast.time :as time]
            [coast.utils :as utils]
            [coast.responses :as responses]
            [coast.env :as env]
            [hiccup.page])
  (:import (clojure.lang ExceptionInfo)
           (java.time Duration)))

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
            (responses/internal-server-error (error-fn request)))))))

(defn wrap-not-found [handler not-found-page]
  (if (nil? not-found-page)
    (fn [request]
      (handler request))
    (fn [request]
      (try
        (handler request)
        (catch ExceptionInfo e
          (let [m (ex-data e)
                type (get m :coast/error)]
            (if (= type :not-found)
              (responses/not-found (not-found-page request))
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
  (let [secret (env/env :secret)
        default-opts {:session {:cookie-name "id"
                                :store (cookie/cookie-store {:key secret})}}]
    (utils/deep-merge defaults/site-defaults default-opts opts)))

(defn diff [start end]
  (let [duration (Duration/between start end)]
    (.toMillis duration)))

(defn log-string [request response start-time]
  (let [ms (diff start-time (time/now))
        {:keys [request-method uri]} request
        request-method (or request-method "N/A")
        uri (or uri "N/A")
        method (-> request-method name string/upper-case)
        status (or (-> response :status) "N/A")]
    (format "%s %s %s %sms" method uri status ms)))

(defn log [request response start-time]
  (println (log-string request response start-time)))

(defn wrap-with-logger [handler]
  (fn [request]
    (let [start-time (time/now)
          response (handler request)]
      (log request response start-time)
      response)))
