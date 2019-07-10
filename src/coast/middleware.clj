(ns coast.middleware
  (:require [ring.middleware.file]
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
            [ring.middleware.head]
            [error.core :as error]
            [coast.time :as time]
            [coast.logger :as logger]
            [coast.utils :as utils]
            [coast.env :as env]
            [coast.response :as response]
            [coast.db.connection :as db.connection]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.edn :as edn]))


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


(defn logger [handler]
  (fn [request]
    (let [now (time/now-millis)
          response (handler request)]
      (logger/log request response now)
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
              :store (ring.middleware.session.cookie/cookie-store {:key (env/env :session-key)})}
    :security {:frame-options :deny}
    :params {:keywordize {:keywordize? true :parse-namespaces? true}}}
   opts))


(def reloader #'ring.middleware.reload/reloader)
(defn reload [handler]
  (if (env/dev?)
    (let [reload! (reloader ["db" "src"] true)]
      (fn [request]
        (reload!)
        (handler request)))
    handler))


(defn render-not-found []
  (let [html (some-> (io/resource "public/404.html") (slurp))]
    (response/render :html
       (or html "404 Not Found")
       :status 404)))


(defn not-found [handler]
  (fn [request]
    (let [response (handler request)]
      (if (nil? response)
        (render-not-found)
        response))))


(defn render-server-error []
  (let [html (some-> (io/resource "public/500.html") (slurp))]
    (response/render :html
       (or html "500 Internal Server Error")
       :status 500)))


(defn server-error
  ([handler custom-fn]
   (fn [request]
     (let [[response error] (error/rescue
                             ((or custom-fn handler) request)
                             :500)]
       (if (nil? error)
         response
         (render-server-error)))))
  ([handler]
   (server-error handler nil)))


(defn content-type? [m k]
  (let [headers (utils/map-keys string/lower-case (:headers m))
        content-type (get headers "content-type" "")]
    (condp = k
      :html (string/starts-with? content-type "text/html")
      :json (string/starts-with? content-type "application/json")
      :text (string/starts-with? content-type "text/plain")
      :xml (string/starts-with? content-type "text/xml")
      false)))


(defn layout [handler layout-fn]
  (fn [request]
    (let [response (handler request)]
      (if (and (vector? (get response :body))
            (content-type? response :html))
        (layout-fn request response)
        response))))


(defn assets
  ([handler]
   (assets handler {}))
  ([handler opts]
   (let [opts (site-defaults opts)]
     (-> handler
         (wrap wrap-absolute-redirects (get-in opts [:responses :absolute-redirects] false))
         (wrap-multi wrap-resource (get-in opts [:static :resources] false))
         (wrap-multi wrap-file (get-in opts [:static :files] false))
         (wrap wrap-content-type (get-in opts [:responses :content-types] false))
         (wrap wrap-default-charset (get-in opts [:responses :default-charset] false))
         (wrap wrap-not-modified (get-in opts [:responses :not-modified-responses] false))))))


(defn security-headers
  ([handler]
   (security-headers handler {}))
  ([handler opts]
   (let [opts (site-defaults opts)]
     (wrap handler wrap-x-headers (:security opts)))))


(defn json-body [handler]
  (fn [{:keys [body] :as request}]
    (if (and (some? body)
             (content-type? request :json))
      (let [s-body (slurp body)
            json (json/read-str s-body)]
        (handler (assoc request
                   :body json :raw-body s-body)))
      (handler request))))


(defn json-response [handler]
  (fn [request]
    (let [response (handler request)
          body (:body response)]
      (if (and (some? body)
               (not (string? body))
               (content-type? response :json))
        (assoc response :body (json/write-str body))
        response))))


(defn json [handler]
  (-> (json-body handler)
      (json-response)))


(defn sessions
  ([handler]
   (sessions handler {}))
  ([handler options]
   (let [options (site-defaults options)]
     (-> handler
         (wrap wrap-anti-forgery   (get-in options [:security :anti-forgery] false))
         (wrap wrap-flash          (get-in options [:session :flash] false))
         (wrap wrap-session        (:session options false))))))


(defn coerce-param [val]
  (cond
    (and (string? val)
         (some? (re-find #"^-?\d+\.?\d*$" val))) (edn/read-string val)
    (and (string? val) (string/blank? val)) (edn/read-string val)
    (and (string? val) (= val "false")) false
    (and (string? val) (= val "true")) true
    (vector? val) (mapv coerce-param val)
    (list? val) (map coerce-param val)
    :else val))


(defn coerce-params [handler]
  (fn [request]
    (let [params (:params request)
          coerced-params (utils/map-vals coerce-param params)
          request (assoc request :params coerced-params
                                 :coerced-params coerced-params
                                 :raw-params params)]
      (handler request))))


(defn body-parser
  ([handler]
   (body-parser handler {}))
  ([handler opts]
   (let [opts (site-defaults opts)]
     (-> handler
         (wrap wrap-keyword-params (get-in opts [:params :keywordize] false))
         (wrap wrap-nested-params (get-in opts [:params :nested] false))
         (wrap wrap-multipart-params (get-in opts [:params :multipart] false))
         (wrap wrap-params (get-in opts [:params :urlencoded] false))
         (coerce-params)))))


(defn cookies
  ([handler]
   (cookies handler {}))
  ([handler options]
   (let [options (site-defaults options)]
     (wrap handler wrap-cookies (get-in options [:cookies] false)))))


(defn head [handler]
  (ring.middleware.head/wrap-head handler))
