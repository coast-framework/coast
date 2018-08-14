(ns coast.components
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
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
  [:form params
   (csrf)
   body])

(defn css
  ([req bundle]
   (let [files (or (get-in req [:coast.assets/bundles bundle])
                   (assets/bundle (env :coast-env) bundle))]
     (for [href files]
       [:link {:href href :type "text/css" :rel "stylesheet"}])))
  ([bundle]
   (css nil bundle)))

(defn js
  ([req bundle]
   (let [files (or (get-in req [:coast.assets/bundles bundle])
                   (assets/bundle (env :coast-env) bundle))]
     (for [src files]
      [:script {:src src :type "text/javascript"}])))
  ([bundle]
   (js nil bundle)))
