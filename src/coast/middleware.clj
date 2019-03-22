(ns coast.middleware
  (:require [coast.time :as time]
            [coast.logger :as logger]
            [coast.router :as router]
            [coast.utils :as utils]
            [coast.responses :as res]
            [coast.env :as env]
            [coast.error :as error]
            [clojure.edn :as edn]
            [clojure.stacktrace :as st]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [ring.middleware.file]
            [ring.middleware.defaults]
            [ring.middleware.keyword-params]
            [ring.middleware.params]
            [ring.middleware.session.cookie]
            [ring.middleware.reload]
            [ring.middleware.not-modified]
            [ring.middleware.content-type]
            [ring.middleware.default-charset]
            [ring.middleware.absolute-redirects]
            [ring.middleware.resource]
            [hiccup2.core :as h])
  (:import (clojure.lang ExceptionInfo)
           (java.time Duration)))

(def wrap-not-modified ring.middleware.not-modified/wrap-not-modified)
(def wrap-content-type ring.middleware.content-type/wrap-content-type)
(def wrap-default-charset ring.middleware.default-charset/wrap-default-charset)
(def wrap-absolute-redirects ring.middleware.absolute-redirects/wrap-absolute-redirects)
(def wrap-resource ring.middleware.resource/wrap-resource)


(defn wrap-keyword-params [handler]
  (ring.middleware.keyword-params/wrap-keyword-params handler {:keywordize? true :parse-namespaces? true}))


(defn wrap-logger [handler]
  (fn [request]
    (let [now (time/now)
          response (handler request)]
      (logger/log request response now)
      response)))


(defn wrap-file [handler opts]
  (if (some? (:storage opts))
    (ring.middleware.file/wrap-file handler (:storage opts))
    handler))


(defn site-defaults [opts]
  (utils/deep-merge
   ring.middleware.defaults/site-defaults
   {:session {:cookie-name "id"
              :store (ring.middleware.session.cookie/cookie-store {:key (or (env/env :secret)
                                                                            (env/env :session-key))})}
    :security {:frame-options :deny}
    :params nil}
   opts))


(defn wrap-defaults [handler opts]
  (ring.middleware.defaults/wrap-defaults handler opts))


(defn wrap-site-defaults [handler]
  (fn [request]
    (let [coast-defaults (site-defaults (:coast/opts request))
          f (wrap-defaults handler coast-defaults)]
      (f request))))


(defn exception-page [request e]
  (str
   (h/html
     [:html {:style "margin: 0; padding: 0;"}
      [:head
       [:title (.getMessage e)]]
      [:body {:style "margin: 0; padding: 0; font-family: -apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,Oxygen-Sans,Ubuntu,Cantarell,\"Helvetica Neue\",sans-serif;"}
       [:div {:style "padding: 1rem; background-color: #333;"}
        [:div
         [:span {:style "color: red"} (type e)]
         [:span {:style "color: #aaa; display: inline-block; margin-left: 10px"}
          (str "at " (:uri request))]]
        [:h1 {:style "color: white; margin: 0; padding: 0"} (.getMessage e)]]
       [:p {:style "background-color: #f4f4f4; padding: 2rem;"}
        (h/raw
          (-> (st/print-stack-trace e)
              (with-out-str)
              (string/replace #"\n" "<br />")
              (string/replace #"\$fn__\d+\.invoke" "")
              (string/replace #"\$fn__\d+\.doInvoke" "")
              (string/replace #"\$" "/")))]]])))


(defn response-map? [m]
  (and (map? m)
       (contains? m :status)
       (contains? m :body)
       (contains? m :headers)))


(defn wrap-exceptions [handler]
  (fn [request]
    (try
      (let [response (handler request)]
        (if (response-map? response)
          (if (vector? (:body response))
            (-> (assoc response :body (str (h/html (:body response))))
                (assoc-in [:headers "content-type"] "text/html; charset=utf-8"))
            response)
          (throw (Exception. (str "Coast error. Expected a response map. Got " response)))))
      (catch Exception e
        (res/server-error (exception-page request e) :html)))))


(defn server-error [request]
  [:html
    [:head
     [:title "Internal Server Error"]]
    [:body
     [:h1 "500 Internal server error"]]])

(defn wrap-site-errors [handler routes]
  (if (not= "prod" (env/env :coast-env))
    (wrap-exceptions handler)
    (fn [request]
      (let [error-fn (router/server-error-fn routes)]
        (try
          (handler request)
          (catch Exception e
            (let [f (or error-fn server-error)
                  response (f (assoc request :exception e
                                             :stacktrace (with-out-str
                                                          (st/print-stack-trace e))))]
              (res/server-error response :html))))))))


(defn layout? [response layout]
  (and (some? layout)
       (or (vector? response)
           (string? response))))


(defn resolve-fn [val]
  (cond
    (keyword? val) (-> val utils/keyword->symbol resolve)
    (fn? val) val
    :else nil))


(defn wrap-layout [handler layout]
  (if (nil? layout)
    handler
    (fn [request]
      (let [layout (resolve-fn layout)
            response (handler request)]
        (cond
          (map? response) response
          (layout? response layout) (-> (layout request response)
                                        (h/html)
                                        (str)
                                        (res/ok :html))
          :else (res/ok response))))))

(defn wrap-not-found [handler routes]
  (fn [request]
    (let [[response error] (error/rescue
                            (handler request)
                            :not-found)]
      (if (nil? error)
        response
        (res/not-found
         ((router/not-found-fn routes) request)
         :html)))))


(defn wrap-with-layout [layout & routes]
  (router/wrap-routes wrap-site-defaults
                      #(wrap-layout % layout)
                      routes))


(defn site-routes [& args]
  (let [[layout-kw routes] (if (keyword? (first args))
                             [(first args) (rest args)]
                             [nil args])]
    (if (some? layout-kw)
      (router/wrap-routes wrap-site-defaults
                          #(wrap-layout % layout-kw)
                          routes)
      (router/wrap-routes wrap-site-defaults
                          routes))))
(defn site [& args]
  (apply site-routes args))


(defn with-layout [layout & routes]
  (apply (partial wrap-with-layout layout) routes))


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
          request (assoc request :params coerced-params
                                 :coerced-params coerced-params
                                 :raw-params params)]
      (handler request))))


(def simulated-methods
  {"put" :put
   "patch" :patch
   "delete" :delete})

(defn wrap-simulated-methods [handler]
  (fn [request]
    (let [method (if (= :post (:request-method request))
                   (get simulated-methods (get-in request [:params :_method]) (:request-method request))
                   (:request-method request))]
      (handler (assoc request :request-method method
                              :original-request-method (:request-method request))))))


(defn wrap-params [handler]
  (ring.middleware.params/wrap-params handler))


(def reloader #'ring.middleware.reload/reloader)
(defn wrap-reload [handler]
  (if (not= "dev" (env/env :coast-env))
    handler
    (let [reload! (reloader ["db" "src"] true)]
      (fn [request]
        (reload!)
        (handler request)))))


(defn wrap-json-params [handler]
  (fn [{:keys [body params] :as request}]
    (if (some? body)
      (let [json-params (-> body slurp json/read-str)
            response (handler (assoc request :params (merge params json-params)
                                             :json-params json-params))]
        response)
      (handler request))))


(defn wrap-json-response [handler]
  (fn [request]
    (let [response (handler request)
          body (:body response)]
      (-> (assoc response :body (json/write-str body))
          (assoc-in [:headers "content-type"] "application/json")))))


(defn wrap-json-response-with-content-type [handler]
  (fn [request]
    (let [{:keys [body headers] :as response} (handler request)
          content-type (-> (utils/map-keys string/lower-case headers)
                           (get "content-type"))]
      (if (and (some? body)
               (not (string? body))
               (string/starts-with? content-type "application/json"))
        (assoc response :body (json/write-str body))
        response))))


(defn api-defaults [opts]
  (utils/deep-merge ring.middleware.defaults/api-defaults opts))


(defn wrap-api-defaults [handler]
  (fn [request]
    (let [f (wrap-defaults handler (api-defaults (:coast/opts request)))]
      (f request))))

(defn wrap-api-not-found [handler routes]
  (fn [request]
    (let [[response error] (error/rescue
                            (handler request)
                            :not-found)]
      (if (nil? error)
        response
        ((router/api-not-found-fn routes) request)))))


(defn wrap-api-errors [handler routes]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (let [error-fn (router/api-server-error-fn routes)
              request (merge request {:message (.getMessage e)
                                      :ex-data (ex-data e)
                                      :stacktrace (with-out-str
                                                   (st/print-stack-trace e))})]
          (if (fn? error-fn)
            (error-fn request)
            (res/server-error request :json)))))))


(defn api-routes [& routes]
  (router/wrap-routes #(wrap-api-errors % routes)
                      #(wrap-api-not-found % routes)
                      wrap-api-defaults
                      wrap-json-params
                      wrap-json-response
                      routes))


(defn api [& routes]
  (apply api-routes routes))


(defn wrap-plain-text-content-type [handler]
  (fn [request]
    (let [response (handler request)
          headers (:headers response)
          headers (utils/map-keys string/lower-case headers)
          content-type (get headers "content-type")]
      (if (or (string/blank? content-type)
              (= "application/octet-stream" content-type))
        (assoc-in response [:headers "Content-Type"] "text/plain")
        response))))


(defn wrap-html-response [handler]
  (fn [request]
    (let [response (handler request)]
      (if (and (vector? (:body response))
               (or (string/starts-with? (get-in response [:headers "content-type"])
                                        "text/html")
                   (string/starts-with? (get-in response [:headers "Content-Type"])
                                        "text/html")))
        (assoc response :body (-> response :body h/html str))
        response))))
