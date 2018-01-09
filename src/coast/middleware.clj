(ns coast.middleware
  (:require [coast.responses :as responses]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.resource :as resource]
            [ring.middleware.flash :as flash]
            [ring.middleware.reload :as reload]
            [environ.core :as environ]
            [bunyan.core :as bunyan]
            [prone.middleware :as prone]
            [trail.core :as trail]
            [coast.utils :as utils]))

(defn wrap-errors [handler error-page]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (responses/internal-server-error error-page)))))

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

(defn wrap-coast-defaults [handler opts]
  (let [{:keys [layout public session cookie-store error-page]} opts
        secret (environ/env :secret)
        max-age (get-in session [:cookie-attrs :max-age] 86400)
        session-store (or cookie-store (cookie/cookie-store {:key secret}))]
    (-> handler
        (trail/wrap-match-routes)
        (wrap-layout layout)
        (bunyan/wrap-with-logger)
        (resource/wrap-resource (or public "public"))
        (defaults/wrap-defaults (-> defaults/site-defaults
                                    (assoc-in [:session :cookie-attrs :max-age] max-age)
                                    (assoc-in [:session :store] session-store)))
        (flash/wrap-flash)
        (wrap-if utils/dev? reload/wrap-reload)
        (wrap-if utils/dev? prone/wrap-exceptions)
        (wrap-if utils/prod? wrap-errors error-page))))
