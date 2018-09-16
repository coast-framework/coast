(ns coast.middleware.api
  (:require [coast.responses :as res]
            [coast.env :refer [env]]
            [coast.utils :as utils]
            [clojure.stacktrace :as st]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]))

(defn wrap-errors [handler {:keys [api/internal-server-error]}]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (let [body {:message (.getMessage e)
                    :ex-data (ex-data e)
                    :uri (:uri request)
                    :stacktrace (with-out-str
                                 (st/print-stack-trace e))}]
          (if (= "dev" (env :coast-env))
            (res/internal-server-error body)
            (if (fn? internal-server-error)
              (internal-server-error body)
              (res/internal-server-error {:error "😵 something went wrong"}))))))))

(defn wrap-json-params [handler]
  (fn [{:keys [body params content-type] :as request}]
    (if (and (some? (re-find #"application/json" (or content-type "")))
             (some? body))
      (let [json-params (-> body slurp json/read-str)]
        (handler (assoc request :params (merge params json-params))))
      (handler request))))

(defn wrap-json-response [handler]
  (fn [request]
    (let [response (handler request)
          accept (get-in request [:headers "accept"])]
      (if (or (nil? accept)
              (string/blank? accept)
              (= "*/*" accept)
              (some? (re-find #"application/json" accept)))
        (-> (update response :body json/write-str)
            (assoc-in [:headers "content-type"] "application/json"))
        response))))

(defn wrap-api-defaults [handler opts]
  (let [;not-found (get opts :api/not-found (resolve `api.error/not-found))
        server-error (get opts :api/internal-server-error (resolve `api.error/internal-server-error))
        api-handler (-> handler
                        (wrap-errors server-error)
                        (wrap-defaults (utils/deep-merge api-defaults opts))
                        (wrap-json-params)
                        (wrap-multipart-params)
                        (wrap-json-response))]
    (fn [request]
      (if (or (true? (:coast.router/api-route? request))
              (some? (re-find #"application/json" (or (get-in request [:headers "accept"])
                                                      ""))))
        (api-handler request)
        (handler request)))))
