(ns coast.components
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [coast.env :refer [env]]))


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

(defn css [req bundle]
  (let [hrefs (get-in req [:coast.assets/bundles bundle])]
    (if (= "prod" (env :coast-env))
      [:link {:href (first hrefs) :type "text/css" :rel "stylesheet"}]
      (for [href hrefs]
        [:link {:href href :type "text/css" :rel "stylesheet"}]))))

(defn js [req bundle]
  (let [hrefs (get-in req [:coast.assets/bundles bundle])]
    (if (= "prod" (env :coast-env))
      [:script {:src (first hrefs) :type "text/javascript"}]
      (for [href hrefs]
       [:script {:src href :type "text/javascript"}]))))
