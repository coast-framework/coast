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
            [clojure.java.io :as io]
            [ring.middleware.file]
            [ring.middleware.keyword-params]
            [ring.middleware.params]
            [ring.middleware.session.cookie]
            [ring.middleware.session]
            [ring.middleware.reload]
            [ring.middleware.not-modified]
            [ring.middleware.content-type]
            [ring.middleware.default-charset]
            [ring.middleware.absolute-redirects]
            [ring.middleware.resource]
            [ring.middleware.anti-forgery]
            [ring.middleware.cookies]
            [ring.middleware.multipart-params]
            [ring.middleware.nested-params]
            [ring.middleware.flash]
            [ring.middleware.x-headers :as x]
            [hiccup2.core :as h])
  (:import (clojure.lang ExceptionInfo)
           (java.time Duration)))

(def wrap-not-modified ring.middleware.not-modified/wrap-not-modified)
(def wrap-content-type ring.middleware.content-type/wrap-content-type)
(def wrap-default-charset ring.middleware.default-charset/wrap-default-charset)
(def wrap-absolute-redirects ring.middleware.absolute-redirects/wrap-absolute-redirects)
(def wrap-params ring.middleware.params/wrap-params)
(def wrap-resource ring.middleware.resource/wrap-resource)
(def wrap-cookies ring.middleware.cookies/wrap-cookies)
(def wrap-multipart-params ring.middleware.multipart-params/wrap-multipart-params)
(def wrap-nested-params ring.middleware.nested-params/wrap-nested-params)
(def wrap-session ring.middleware.session/wrap-session)
(def wrap-flash ring.middleware.flash/wrap-flash)
(def wrap-keyword-params ring.middleware.keyword-params/wrap-keyword-params)
(def wrap-anti-forgery ring.middleware.anti-forgery/wrap-anti-forgery)


(defn wrap-xss-protection [handler options]
  (x/wrap-xss-protection handler (:enable? options true) (dissoc options :enable?)))


(defn wrap [handler middleware options]
  (if (true? options)
    (middleware handler)
    (if options
      (middleware handler options)
      handler)))


(defn wrap-multi [handler middleware args]
  (wrap handler
        (fn [handler args]
          (if (coll? args)
            (reduce middleware handler args)
            (middleware handler args)))
        args))


(defn wrap-x-headers [handler options]
  (-> handler
      (wrap wrap-xss-protection         (:xss-protection options false))
      (wrap x/wrap-frame-options        (:frame-options options false))
      (wrap x/wrap-content-type-options (:content-type-options options false))))


(defn wrap-logger [handler log-fn]
  (fn [request]
    (let [now (time/now)
          response (handler request)
          f (or log-fn logger/log)]
      (when (and (fn? f)
                 (not (false? log-fn)))
        (f request response now))
      response)))


(defn wrap-file [handler opts]
  (if (some? (:storage opts))
    (ring.middleware.file/wrap-file handler (:storage opts))
    handler))


; credit: ring.middleware.defaults/site-defaults
(def ring-site-defaults
 "A default configuration for a browser-accessible website, based on current
  best practice."
 {:params    {:urlencoded true
              :multipart  true
              :nested     true
              :keywordize true}
  :cookies   true
  :session   {:flash true
              :cookie-attrs {:http-only true, :same-site :strict}}
  :security  {:anti-forgery   true
              :xss-protection {:enable? true, :mode :block}
              :frame-options  :sameorigin
              :content-type-options :nosniff}
  :static    {:resources "public"}
  :responses {:not-modified-responses true
              :absolute-redirects     true
              :content-types          true
              :default-charset        "utf-8"}})


(defn site-defaults [opts]
  (utils/deep-merge
   ring-site-defaults
   {:session {:cookie-name "id"
              :store (ring.middleware.session.cookie/cookie-store {:key (or (env/env :secret)
                                                                            (env/env :session-key))})}
    :security {:frame-options :deny}
    :params {:keywordize {:keywordize? true :parse-namespaces? true}}}
   opts))


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


(defn wrap-exceptions [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (res/server-error (exception-page request e) :html)))))


(defn server-error [request]
  [:html
    [:head
     [:title "Internal Server Error"]]
    [:body
     [:h1 "500 Internal server error"]]])


(defn public-server-error [_]
  (let [r (io/resource "public/500.html")]
    (if (nil? r)
      "500 Internal Server Error"
      (slurp r))))


(defn wrap-site-errors [handler routes]
  (if (not= "prod" (env/env :coast-env))
    (wrap-exceptions handler)
    (fn [request]
      (let [error-fn (or (router/server-error-fn routes)
                         (utils/resolve-safely `site.home/server-error)
                         (utils/resolve-safely `home/server-error)
                         public-server-error)]
        (try
          (handler request)
          (catch Exception e
            (let [f (or error-fn server-error)
                  response (f (assoc request :exception e
                                             :stacktrace (with-out-str
                                                          (st/print-stack-trace e))))]
              (res/server-error response :html))))))))


(defn public-not-found [_]
  (let [r (io/resource "public/404.html")]
    (if (nil? r)
      "404 Not Found"
      (slurp r))))


(defn wrap-not-found [handler routes]
  (fn [request]
    (let [[response error] (error/rescue
                            (handler request)
                            :not-found)]
      (if (nil? error)
        response
        (let [not-found-fn (or (router/not-found-fn routes)
                               (utils/resolve-safely `site.home/not-found)
                               (utils/resolve-safely `home/not-found)
                               public-not-found)]
          (res/not-found
           (not-found-fn request)
           :html))))))


(defn resolve-fn [val]
  (cond
    (keyword? val) (-> val utils/keyword->symbol resolve)
    (fn? val) val
    :else nil))


(defn wrap-layout [handler layout]
  (fn [request]
    (let [response (handler request)]
      (if (vector? response)
        (-> (layout request response)
            (h/html)
            (str)
            (res/ok :html))
        response))))


(defn wrap-with-layout [layout & routes]
  (let [layout-fn (resolve-fn layout)]
    (if (nil? layout-fn)
      (throw (Exception. "with-layout requires a layout function in the first argument"))
      (router/wrap-routes #(wrap-layout % layout-fn) routes))))


(defn with-layout [layout & routes]
  (apply (partial wrap-with-layout layout) routes))


(defn content-type? [m k]
  (let [headers (utils/map-keys string/lower-case (:headers m))
        content-type (get headers "content-type" "")]
    (condp = k
      :html (string/starts-with? content-type "text/html")
      :json (string/starts-with? content-type "application/json")
      false)))


(defn wrap-html-response [handler]
  (fn [request]
    (let [response (handler request)]
      (if (and (vector? (:body response))
               (content-type? response :html))
        (update response :body #(-> % h/html str))
        response))))


(defn site-middleware [handler opts]
  (-> handler
      (wrap wrap-anti-forgery (get-in opts [:security :anti-forgery] false))
      (wrap wrap-flash (get-in opts [:session :flash] false))
      (wrap wrap-session (get opts :session false))
      (wrap wrap-cookies (get-in opts [:cookies] false))))


(defn ring-response-html [handler]
  (fn [request]
    (let [response (handler request)]
      (cond
        (vector? response) (res/ok response :html)
        (string? response) (res/ok response)
        (map? response) response
        (nil? response) (res/ok "" :html)
        :else (throw (Exception. "You can only return vectors, maps and strings from handler functions"))))))


(defn site-routes [& args]
  (let [[opts routes] (if (map? (first args))
                        [(first args) (rest args)]
                        [{} args])
        opts (site-defaults opts)]
    (router/wrap-routes #(site-middleware % opts)
                        ring-response-html
                        routes)))


(defn site [& args]
  (apply site-routes args))


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


(def reloader #'ring.middleware.reload/reloader)
(defn wrap-reload [handler]
  (if (not= "dev" (env/env :coast-env))
    handler
    (let [reload! (reloader ["db" "src"] true)]
      (fn [request]
        (reload!)
        (handler request)))))


(defn wrap-json-body [handler]
  (fn [{:keys [body] :as request}]
    (if (and (some? body)
             (content-type? request :json))
      (let [s-body (slurp body)
            json (-> s-body json/read-str)]
        (handler (assoc request :body json
                                :json json
                                :raw-body s-body
                                :json-params (when (map? json)
                                               json))))
      (handler request))))


(defn wrap-json-response [handler]
  (fn [request]
    (let [response (handler request)
          body (:body response)]
      (if (and (some? body)
               (not (string? body))
               (content-type? response :json))
        (assoc response :body (json/write-str body))
        response))))


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


(defn ring-response-json [handler]
  (fn [request]
    (let [response (handler request)]
      (cond
        (vector? response) (res/ok response :json)
        (and (map? response)
             (contains? response :status)
             (contains? response :headers)) (assoc-in response [:headers "content-type"] "application/json")
        (map? response) (res/ok response :json)
        (string? response) (res/ok response :json)
        (nil? response) (res/ok "" :json)
        :else (throw (Exception. "You can only return vectors, maps and strings from handler functions"))))))


(defn api-routes [& routes]
  (router/wrap-routes ring-response-json routes))


(defn api [& routes]
  (apply api-routes routes))


(defn wrap-plain-text-response [handler]
  (fn [request]
    (let [response (handler request)
          headers (:headers response)
          headers (utils/map-keys string/lower-case headers)
          content-type (get headers "content-type")]
      (if (or (string/blank? content-type)
              (= "application/octet-stream" content-type))
        (assoc-in response [:headers "Content-Type"] "text/plain")
        response))))
