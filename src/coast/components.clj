(ns coast.components
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [clojure.string :as string]
            [trail.core :as trail]))

(defn csrf
  ([attrs]
   [:input (assoc attrs :type "hidden"
                        :name "__anti-forgery-token"
                        :value *anti-forgery-token*)])
  ([]
   (csrf {})))

(defn method [m]
  (if (nil? (:id m))
    :post
    :put))

(defn form [attrs & content]
  (let [hidden-method (when (or (not= :get (:method attrs))
                                (not= :post (:method attrs)))
                        [:input {:type "hidden" :name "_method" :value (:method attrs)}])]
    [:form (merge attrs {:method :post})
     hidden-method
     (csrf)
     content]))

(defn form-for [routes route-name attrs & content]
  (let [action (trail/url-for routes route-name attrs)
        method (method attrs)]
    (form {:method method
           :action action}
      content)))

(defn field
  ([attrs k v]
   [:input (assoc attrs :name (name k)
                        :value v)])
  ([attrs k]
   (field attrs k "")))

(defn link-to [routes route-name attrs & content]
  (let [href (or (:href attrs)
                 (trail/url-for routes route-name attrs))]
    [:a {:href (:href attrs)}
     content]))

