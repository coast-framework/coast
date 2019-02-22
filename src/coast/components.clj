(ns coast.components
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [coast.env :refer [env]]
            [coast.assets :as assets]))

(defn csrf
  ([attrs]
   [:input (assoc attrs :type "hidden"
                        :name "__anti-forgery-token"
                        :value *anti-forgery-token*)])
  ([]
   (csrf {})))

(defn form [params & body]
  [:form (dissoc params :_method)
   (csrf)
   (when (contains? #{:patch :put :delete} (:_method params))
     [:input {:type "hidden" :name "_method" :value (:_method params)}])
   body])

(defn css
  ([req bundle]
   (let [files (assets/bundle (env :coast-env) bundle)]
     (for [href files]
       [:link {:href href :type "text/css" :rel "stylesheet"}])))
  ([bundle]
   (css nil bundle)))

(defn js
  ([req bundle]
   (let [files (assets/bundle (env :coast-env) bundle)]
     (for [src files]
      [:script {:src src :type "application/javascript" :defer true}])))
  ([bundle]
   (js nil bundle)))
