(ns coast.middleware
  (:require [ring.middleware.defaults :as middleware.defaults :refer [site-defaults api-defaults]]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.file :refer [wrap-file]]
            [clojure.stacktrace :as st]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [coast.time :as time]
            [coast.utils :as utils]
            [coast.responses :as responses]
            [coast.env :as env]
            [coast.dev.middleware :as dev.middleware]
            [coast.logger :as logger]
            [coast.error :refer [rescue]]
            [hiccup.core :as h])
  (:import (clojure.lang ExceptionInfo)
           (java.time Duration)))

(defn internal-server-error []
  (responses/internal-server-error
    [:html
      [:head
       [:title "Internal Server Error"]]
      [:body
       [:h1 "500 Internal server error"]]]))

(defn wrap-errors [handler error-fn]
  (if (= "prod" (env/env :coast-env))
    (fn [request]
      (try
        (handler request)
        (catch Exception e
          (responses/internal-server-error
            ((or error-fn internal-server-error)
             (assoc request :exception e
                            :stacktrace (with-out-str
                                         (st/print-stack-trace e))))))))
    (dev.middleware/wrap-exceptions handler)))

(defn wrap-not-found [handler not-found-page]
  (if (nil? not-found-page)
    handler
    (fn [request]
      (let [[response errors] (rescue
                               (handler request)
                               :404)]
        (if (nil? errors)
          response
          (responses/not-found
            (not-found-page request)))))))

(defn layout? [response layout]
  (and (some? layout)
       (or (vector? response)
           (string? response))))

(defn wrap-layout [handler layout]
  (fn [request]
    (let [response (handler request)]
      (cond
        (map? response) response
        (layout? response layout) (responses/ok (layout request response))
        :else (responses/ok response)))))

(defn wrap-json-response [handler opts]
  (fn [request]
    (let [response (handler request)
          accept (get-in request [:headers "accept"])]
      (if (or (= :api (:wrap-defaults opts))
              (and (some? accept)
                   (some? (re-find #"application/json" accept))))
        (-> (update response :body json/write-str)
            (assoc :headers {"content-type" "application/json"}))
        response))))

(defn wrap-html-response [handler]
  (fn [request]
    (let [{:keys [body] :as response} (handler request)]
      (if (vector? body)
        (assoc response :body (h/html body)
                        :headers {"content-type" "text/html"})
        response))))

(defn wrap-json-params [handler opts]
  (fn [{:keys [body params content-type] :as request}]
    (if (and (some? body)
             (or (= :api (:wrap-defaults opts))
                 (= "application/json" content-type)))
      (let [s (slurp body)
            parsed-params (if (some? body)
                            (json/read-str s)
                            body)]
        (handler (assoc request :params (merge params parsed-params))))
      (handler request))))

(defn wrap-api
  ([handler opts]
   (let [m (utils/deep-merge api-defaults {:params {:keywordize? false}} opts)
         handler (middleware.defaults/wrap-defaults handler m)]
     (fn [request]
       (handler (utils/deep-merge request {:wrap-defaults :api
                                           :headers {"content-type" "application/json"
                                                     "accept" "application/json"}
                                           :content-type "application/json"})))))
  ([handler]
   (wrap-api handler {})))

(defn wrap-site
  ([handler opts]
   (fn [request]
     (if (or (= :api (:wrap-defaults opts))
             (= :api (:wrap-defaults request)))
       (handler request)
       (let [secret (env/env :secret)
             session-opts {:session {:cookie-name "id"
                                     :store (cookie/cookie-store {:key secret})}}
             m (utils/deep-merge site-defaults session-opts {:params {:keywordize? false}} opts)
             handler (-> (middleware.defaults/wrap-defaults handler m)
                         (wrap-html-response))]
         (handler request)))))
  ([handler]
   (wrap-site handler {})))

(defn wrap-defaults [handler opts]
  (condp = (:wrap-defaults opts)
    false handler
    :api (wrap-api handler opts)
    (wrap-site handler opts)))

(defn wrap-with-logger [handler]
  (fn [request]
    (let [now (time/now)
          response (handler request)]
      (logger/log request response now)
      response)))

(defn coerce-params [val]
  (cond
    (and (string? val)
         (some? (re-find #"^-?\d+\.?\d*$" val))) (edn/read-string val)
    (and (string? val) (string/blank? val)) (edn/read-string val)
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

(defn wrap-storage [handler s]
  (if (nil? s)
    handler
    (wrap-file handler s)))
