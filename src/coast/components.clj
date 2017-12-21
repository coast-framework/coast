(ns coast.components
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [trail.core :as trail]))

(defn csrf
  ([attrs]
   [:input (assoc attrs :type "hidden"
                        :name "__anti-forgery-token"
                        :value *anti-forgery-token*)])
  ([]
   (csrf {})))

(defn hidden-method? [method]
  (or (= :put method)
      (= :patch method)
      (= :delete method)))

(defn form-for [v & content]
  (let [[method] v
        action (trail/action-for v)
        method-str (name (or method ""))]
    [:form {:method method-str :action action}
      (csrf)
      (when (hidden-method? method)
        [:input {:type "hidden" :name "_method" :value method-str}])
      content]))

(defn link-to
  ([s v params]
   (let [href (trail/url-for v)]
     [:a (merge {:href href} params)
      s]))
  ([s v]
   (link-to s v {})))
