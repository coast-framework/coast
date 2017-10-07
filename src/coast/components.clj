(ns coast.components
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [hiccup.form :as form]))

(defn csrf
  ([attrs]
   (form/hidden-field attrs "__anti-forgery-token" *anti-forgery-token*))
  ([]
   (csrf {})))
