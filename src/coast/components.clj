(ns coast.components
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

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
