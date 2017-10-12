(ns coast.middleware
  (:require [coast.responses :as responses]
            [coast.utils :as utils]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.resource :as resource]
            [environ.core :as environ]
            [bunyan.core :as bunyan]
            [prone.middleware :as prone]
            [com.jakemccrary.middleware.reload :as reload]))

(defn layout? [response layout]
  (and (not (map? response))
       (not (nil? layout))))

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
          request (assoc request :params (utils/map-vals utils/coerce-string params))]
      (handler request))))

(defn wrap-coast-defaults [handler config]
  (let [{:keys [layout public]} config]
    (-> handler
        (wrap-coerce-params)
        (wrap-layout layout)
        (bunyan/wrap-with-logger)
        (resource/wrap-resource (or public "public"))
        (defaults/wrap-defaults (-> defaults/site-defaults
                                    (assoc-in [:session :cookie-attrs :max-age] 86400)
                                    (assoc-in [:session :store] (cookie/cookie-store {:key (environ/env :secret)})))))))

(defn dev [handler]
  (-> handler
      reload/wrap-reload
      prone/wrap-exceptions))

