(ns coast.components
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [clojure.string :as string]))

(defn csrf
  ([attrs]
   [:input (assoc attrs :type "hidden"
                        :name "__anti-forgery-token"
                        :value *anti-forgery-token*)])
  ([]
   (csrf {})))

(defn uri [ks & maps]
  (let [space (namespace (last ks))
        names (mapv #(or (namespace %) (name %)) ks)
        ids (mapv #(or nil (:id %)) (or maps [{}]))
        parts (->> (interleave names ids)
                   (filter #(not (nil? %))))
        parts (if (not (nil? (namespace (last ks))))
                (concat parts [(name (last ks))])
                parts)]
    (str "/" (string/join "/" parts))))

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

(defn form-for [ks maps & content]
  (let [action (apply (partial uri ks) maps)
        method (method (last maps))]
    (form {:method method
           :action action}
      content)))

(defn field
  ([attrs k v]
   [:input (assoc attrs :name (name k)
                        :value v)])
  ([attrs k]
   (field attrs k "")))

(defn link-to
  ([s ks & maps]
   (let [url (apply (partial uri ks) maps)]
     [:a {:href url} s]))
  ([s k]
   (link-to s k {})))
