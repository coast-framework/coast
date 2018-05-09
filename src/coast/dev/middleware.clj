(ns coast.dev.middleware
  (:require [coast.responses :as res]
            [clojure.stacktrace :as st]
            [clojure.string :as string]))

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
     (-> (st/print-stack-trace e)
         (with-out-str)
         (string/replace #"\n" "<br />")
         (string/replace #"\$fn__\d+\.invoke" "")
         (string/replace #"\$fn__\d+\.doInvoke" "")
         (string/replace #"\$" "/"))]]])

(defn wrap-exceptions [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (res/internal-server-error
          (exception-page request e))))))
