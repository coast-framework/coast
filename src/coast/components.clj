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
