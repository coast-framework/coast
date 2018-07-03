(ns coast.middleware
  (:require [ring.middleware.defaults :as defaults]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.reload :as reload]
            [clojure.stacktrace :as st]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [coast.time :as time]
            [coast.utils :as utils]
            [coast.responses :as responses]
            [coast.env :as env]
            [coast.dev.middleware :as dev.middleware]
            [coast.logger :as logger])
  (:import (clojure.lang ExceptionInfo)
           (java.time Duration)))

(defn internal-server-error []
  [:html
    [:head
     [:title "Internal Server Error"]]
    [:body
     [:h1 "500 Internal server error"]]])

(defn wrap-errors [handler error-fn]
  (if (= "prod" (env/env :coast-env))
    (fn [request]
      (try
        (handler request)
        (catch Exception e
          (st/print-stack-trace e)
          (responses/internal-server-error
            (or (error-fn (assoc request :exception e
                                         :stacktrace (with-out-str (st/print-stack-trace e))))
                (internal-server-error))))))
    (dev.middleware/wrap-exceptions handler)))

(defn wrap-not-found [handler not-found-page]
  (if (nil? not-found-page)
    handler
    (fn [request]
      (utils/try+
        (handler request)
        (fn [ex]
          (when (= 404 (:type ex))
            (responses/not-found
             (not-found-page request))))))))

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

(defn coast-defaults [opts]
  (let [secret (env/env :secret)
        default-opts {:session {:cookie-name "id"
                                :store (cookie/cookie-store {:key secret})}
                      :params {:keywordize? false}}]
    (utils/deep-merge defaults/site-defaults default-opts opts)))

(defn wrap-with-logger [handler]
  (fn [request]
    (let [now (time/now)
          response (handler request)]
      (logger/log request response now)
      response)))

(defn wrap-reload [handler]
  (if (= "dev" (env/env :coast-env))
    (reload/wrap-reload handler)
    handler))

(defn booleans? [val]
  (and (vector? val)
       (every? #(or (= % "true")
                    (= % "false")) val)))

(defn coerce-params [val]
  (cond
    (and (string? val)
         (some? (re-find #"^-?\d+\.?\d*$" val))) (edn/read-string val)
    (and (string? val) (string/blank? val)) (edn/read-string val)
    (booleans? val) (edn/read-string (last val))
    (and (string? val) (= val "false")) false
    (and (string? val) (= val "true")) true
    (vector? val) (mapv coerce-params val)
    (list? val) (map coerce-params val)
    :else val))

(defn wrap-coerce-params [handler]
  (fn [request]
    (let [params (:params request)
          coerced-params (utils/map-vals coerce-params params)
          request (assoc request :params coerced-params)]
      (handler request))))

(defn wrap-route-middleware [handler]
  (fn [request]
    (let [middleware (:route/middleware request)]
      (if (nil? middleware)
        (handler request)
        ((middleware handler) request)))))
