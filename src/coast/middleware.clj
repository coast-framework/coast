(ns coast.middleware
  (:require [coast.responses :as responses]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.reload :as reload]
            [environ.core :as environ]
            [bunyan.core :as bunyan]
            [prone.middleware :as prone]
            [trail.core :as trail]
            [coast.utils :as utils]
            [clojure.stacktrace :as st])
  (:import (clojure.lang ExceptionInfo)))

(defn wrap-errors [handler error-page]
  (if (nil? error-page)
    (fn [request]
      (handler request))
    (fn [request]
      (try
        (handler request)
        (catch Exception e
          (println (st/print-stack-trace e))
          (responses/internal-server-error (error-page)))))))

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

(defn deep-merge [& ms]
  (apply merge-with
         (fn [& vs]
           (if (every? map? vs)
             (apply deep-merge vs)
             (last vs)))
         ms))

(defn coast-defaults [opts]
  (let [secret (environ/env :secret)
        default-opts {:session {:cookie-name "id"
                                :store (cookie/cookie-store {:key secret})}}]
    (deep-merge defaults/site-defaults default-opts opts)))

(defn wrap-coast-defaults
  ([handler opts]
   (let [{:keys [layout error-page not-found-page]} opts]
     (-> handler
         (trail/wrap-match-routes)
         (wrap-layout layout)
         (bunyan/wrap-with-logger)
         (defaults/wrap-defaults (coast-defaults opts))
         (wrap-not-found not-found-page)
         (wrap-if utils/dev? reload/wrap-reload)
         (wrap-if utils/dev? prone/wrap-exceptions)
         (wrap-if utils/prod? wrap-errors error-page))))
  ([handler]
   (wrap-coast-defaults handler {})))
