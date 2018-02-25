(ns coast.components
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [coast.router :as router]
            [hiccup.page]))

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
  (let [[method _ _ params] v
        action (router/action v)
        method-str (if (= :get method) "get" "post")]
    [:form (merge params {:method method-str :action action})
     (csrf)
     (when (hidden-method? method)
       [:input {:type "hidden" :name "_method" :value (name method)}])
     content]))

(defn link-to
  ([s v params]
   (let [href (router/url v)]
     [:a (merge {:href href} params)
      s]))
  ([s v]
   (link-to s v {})))
