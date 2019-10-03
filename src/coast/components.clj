(ns coast.components
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [assets.core :as assets]
            [env.core :as env]
            [hiccup.page]))


(defn doctype [k]
  (get hiccup.page/doctype k))


(defn css
  ([req bundle opts]
   (let [files (assets/bundle (env/env :coast-env) bundle)]
     (for [href files]
       [:link (merge {:href href :type "text/css" :rel "stylesheet"} opts)])))
  ([req bundle]
   (css nil bundle {}))
  ([bundle]
   (css nil bundle)))


(defn js
  ([req bundle opts]
   (let [files (assets/bundle (env/env :coast-env) bundle)]
     (for [src files]
      [:script (merge {:src src :type "application/javascript"} opts)])))
  ([req bundle]
   (js nil bundle {}))
  ([bundle]
   (js nil bundle)))


(defn csrf
  ([attrs]
   [:input (assoc attrs :type "hidden"
                        :name "__anti-forgery-token"
                        :value *anti-forgery-token*)])
  ([]
   (csrf {})))


(defn simulated-method? [method]
  (contains? #{:patch :put :delete} method))


(defn form [params & body]
  (let [{:keys [_method]} params]
    [:form (dissoc params :_method)
     (csrf)
     (when (simulated-method? _method)
       [:input {:type "hidden" :name "_method" :value _method}])
     body]))
