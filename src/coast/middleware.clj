(ns coast.middleware
  (:require [coast.responses :as responses]
            [coast.utils :as utils]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.resource :as resource]
            [ring.middleware.flash :as flash]
            [ring.middleware.reload :as reload]
            [environ.core :as environ]
            [bunyan.core :as bunyan]
            [prone.middleware :as prone]
            [trail.core :as trail]))

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

(defn wrap-coerce-params [handler]
  (fn [request]
    (let [{:keys [params]} request
          request (assoc request :params (utils/map-vals utils/coerce-params params))]
      (handler request))))

(defn wrap-coast-defaults [handler config]
  (let [{:keys [layout public session cookie-store]} config
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
        (flash/wrap-flash))))

(defn dev [handler]
  (-> handler
      reload/wrap-reload
      prone/wrap-exceptions))

