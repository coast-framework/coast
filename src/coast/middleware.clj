(ns coast.middleware
  (:require [ring.middleware.defaults :as defaults]
            [ring.middleware.session.cookie :as cookie]
            [com.jakemccrary.middleware.reload :as reload]
            [clojure.stacktrace :as st]
            [clojure.string :as string]
            [clojure.pprint :refer [pprint]]
            [coast.time :as time]
            [coast.utils :as utils]
            [coast.responses :as responses]
            [coast.env :as env]
            [coast.errors :as errors])
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
          (or (error-fn (assoc request :exception e
                                       :stacktrace (with-out-str (st/print-stack-trace e))))
              (internal-server-error)))))
    handler))

(defn wrap-not-found [handler not-found-page]
  (if (nil? not-found-page)
    handler
    (fn [request]
      (let [response (-> (handler request)
                         (errors/catch+))]
        (if (errors/not-found? response)
          (not-found-page request)
          response)))))

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

(defn req-method [request]
  (or (-> request :params :_method keyword) (:request-method request)))

(defn response-log-string [request response start-time]
  (let [ms (diff start-time (time/now))
        {:keys [uri]} request
        uri (or uri "N/A")
        status (or (-> response :status) "N/A")
        method (-> (req-method request) name string/upper-case)]
    (utils/fill {:uri uri
                 :ms ms
                 :status status
                 :method method}
                "Response to :method: :uri: with status :status: completed in :ms:ms")))

(defn compact-log-string [request response start-time]
  (let [ms (diff start-time (time/now))
        uri (:uri request)
        status (:status response)
        method (-> (req-method request) name string/upper-case)]
    (utils/long-str
      (utils/fill {:uri uri
                   :ms ms
                   :status status
                   :method method}
                  ":method: :uri: :status: :ms:ms")
      (when (and (= "dev" (env/env :coast-env))
                 (not (empty? (:params request))))
        (with-out-str (pprint (:params request)))))))

(defn log-response [request response start-time]
  (println (compact-log-string request response start-time)))

(defn wrap-with-logger [handler]
  (fn [request]
    (let [then (time/now)
          response (handler request)]
      (log-response request response then)
      response)))

(defn wrap-reload [handler]
  (if (= "dev" (env/env :coast-env))
    (reload/wrap-reload handler)
    handler))
