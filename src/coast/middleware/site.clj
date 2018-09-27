(ns coast.middleware.site
  (:require [ring.middleware.defaults :refer [wrap-defaults site-defaults api-defaults]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.session.cookie :as cookie]
            [clojure.stacktrace :as st]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [coast.time :as time]
            [coast.utils :as utils]
            [coast.responses :as res]
            [coast.env :refer [env]]
            [coast.logger :as logger]
            [hiccup2.core :as h])
  (:import (clojure.lang ExceptionInfo)
           (java.time Duration)))

(defn exception-page [request e]
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
           (string/replace #"\$" "/")))]]])

(defn wrap-exceptions [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (res/server-error
          (exception-page request e))))))

(defn server-error [request]
  (res/server-error
    [:html
      [:head
       [:title "Internal Server Error"]]
      [:body
       [:h1 "500 Internal server error"]]]))

(defn wrap-errors [handler error-fn]
  (if (= "dev" (env :coast-env))
    (wrap-exceptions handler)
    (fn [request]
      (try
        (handler request)
        (catch Exception e
          (res/server-error
            ((or error-fn server-error)
             (assoc request :exception e
                            :stacktrace (with-out-str
                                         (st/print-stack-trace e))))))))))

(defn layout? [response layout]
  (and (some? layout)
       (or (vector? response)
           (string? response))))

(defn wrap-layout [handler layout]
  (fn [request]
    (let [response (handler request)]
      (cond
        (map? response) response
        (layout? response layout) (res/ok (layout request response))
        :else (res/ok response)))))

(defn wrap-html-response [handler]
  (fn [request]
    (let [{:keys [body] :as response} (handler request)
          accept (get-in request [:headers "accept"] "")]
      (if (and (vector? body)
               (or (= "*/*" accept)
                   (string/blank? accept)
                   (some? (re-find #"text/html" accept))))
        (-> (assoc response :body (str (h/html body)))
            (assoc-in [:headers "content-type"] "text/html"))
        response))))

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

(defn wrap-site-defaults [handler opts]
  (let [layout (get opts :layout (resolve `components/layout))
        server-error (get opts :site/server-error (resolve `error.server-error/view))
        m (utils/deep-merge
           {:session {:cookie-name "id"
                      :store (cookie/cookie-store {:key (env :secret)})}
            :params {:keywordize? false}}
           site-defaults
           opts)
        site-handler (-> handler
                         (wrap-layout layout)
                         (wrap-keyword-params {:keywordize? true :parse-namespaces? true})
                         (wrap-coerce-params)
                         (wrap-errors server-error)
                         (wrap-html-response)
                         (wrap-defaults m))]
    (fn [request]
      (if (:coast.router/api-route? request)
        (handler request)
        (site-handler request)))))
