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

(defn uri [coll]
  (str "/" (string/join "/" coll)))

(defn method [m]
  (if (nil? (:id m))
    :post
    :put))

(defn action [k m]
  (let [n (name k)]
    (if (nil? (:id m))
      (uri [n])
      (uri [n (:id m)]))))

(defn form [attrs & content]
  (let [hidden-method (when (or (not= :get (:method attrs))
                                (not= :post (:method attrs)))
                        [:input {:type "hidden" :name "_method" :value (:method attrs)}])]
    [:form (merge attrs {:method :post})
     hidden-method
     (csrf)
     content]))

(defn form-for [k m & content]
  (let [action (action k m)
        method (method m)]
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
  ([s k & m]
   (let [n (name k)
         spaces (-> (namespace k)
                  (or n)
                  (string/split #"\."))
         ids (mapv :id m)
         parts (vec (interleave spaces ids))
         parts (if (and (not= n (first spaces)))
                 (conj parts n)
                 parts)
         href (str "/" (string/join "/" (filter #(not (nil? %)) parts)))]
     (if (= (count ids) (count spaces))
       [:a {:href href} s]
       (throw (Exception. "Mismatched number of maps in arguments and namespaces in keyword.")))))
  ([s k]
   (link-to s k {})))
