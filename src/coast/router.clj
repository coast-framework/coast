(ns coast.router
  (:require [clojure.string :as string]
            [clojure.edn :as edn]
            [hiccup.page]
            [coast.responses :as responses]
            [coast.utils :as utils]
            [clojure.java.io :as io])
  (:refer-clojure :exclude [get]))

(def param-re #":([\w-_]+)")

(defn replacement [match m]
  (let [default (first match)
        k (-> match last keyword)]
    (str (clojure.core/get m k default))))

(defn route-str [s m]
  (string/replace s param-re #(replacement % m)))

(def verbs #{:get :post :put :patch :delete})

(defn verb? [value]
  (contains? verbs value))

(defn method-verb? [value]
  (-> (disj verbs :get)
      (contains? value)))

(defn param-method [method]
  (when (method-verb? method)
    (str "?_method=" (name (or method "")))))

(defn uri-for
  "Generates a uri based on method, route syntax and params"
  [v]
  (when (and (vector? v)
             (not (empty? v))
             (every? (comp not nil?) v))
    (let [[arg1 arg2 arg3] v
          [_ route params] (if (not (verb? arg1))
                             [:get arg1 arg2]
                             [arg1 arg2 arg3])]
      (route-str route params))))

(defn url
  "Generates a url based on http method, route syntax and params"
  [v]
  (let [uri (uri-for v)
        [method] v]
    (str uri (param-method method))))

(defn action
  "Generates a form action based on http method"
  [v]
  (str "" (uri-for v)))

(defn params [s]
  (->> (re-seq param-re s)
       (map last)
       (map keyword)))

(defn pattern [s]
  (->> (string/replace s param-re "([A-Za-z0-9-_~]+)")
       (re-pattern)))

(defn route-params [req-uri route-uri]
  (when (every? string? [req-uri route-uri])
    (let [ks (params route-uri)
          param-pattern (pattern route-uri)]
      (->> (re-seq param-pattern req-uri)
           (first)
           (drop 1)
           (zipmap ks)))))

(defn route? [val]
  (and (vector? val)
       (every? some? val)))

(defn match [request-route route]
  (when (every? route? [request-route route])
    (let [[request-method request-uri] request-route
          [route-method route-uri] route
          params (route-params request-uri route-uri)]
      (and (= request-method route-method)
           (= request-uri (route-str route-uri params))))))

(defn wrap-route-with [route middleware]
  "Wraps a single route in a ring middleware fn"
  (if (nil? middleware)
    route
    (let [[method uri val] route]
      (if (vector? val)
        [method uri (conj val middleware)]
        [method uri (conj [val] middleware)]))))

(defn booleans? [val]
  (and (vector? val)
       (every? #(or (= % "true")
                    (= % "false")) val)))

(defn coerce-params [val]
  (cond
    (and (string? val)
         (some? (re-find #"^-?\d+\.?\d*$" val))) (edn/read-string val)
    (and (string? val) (string/blank? val)) (edn/read-string val)
    (booleans? val) (edn/read-string (last val))
    (and (string? val) (= val "false")) false
    (and (string? val) (= val "true")) true
    (vector? val) (mapv coerce-params val)
    (list? val) (map coerce-params val)
    :else val))

(defn default-not-found-fn [_]
  (responses/not-found
    (hiccup.page/html5
      [:head
       [:title "Not Found"]]
      [:body
       [:h1 "404 Page not found"]])))

(defn resolve-route-fn [f not-found-fn]
  (or f not-found-fn default-not-found-fn))

(defn resolve-route [val not-found-fn]
  (if (vector? val)
    (let [f (-> (first val) (resolve-route-fn not-found-fn))
          middleware (->> (rest val)
                          (map #(resolve-route-fn % not-found-fn))
                          (apply comp))]
      (middleware f))
    (resolve-route-fn val not-found-fn)))

(defn match-routes [routes not-found-fn]
  "Turns routes into a ring handler"
  (fn [request]
    (let [{:keys [request-method uri params]} request
          method (or (-> params :_method keyword) request-method)
          route (-> (filter #(match [method uri] %) routes)
                    (first))
          [_ route-uri f] route
          trail-params (route-params uri route-uri)
          params (merge params trail-params)
          handler (resolve-route f not-found-fn)
          coerced-params (utils/map-vals coerce-params params)
          request (assoc request :params coerced-params
                                 ::params params)]
      (handler request))))

(defn paths [s]
  (->> (io/file s)
       (file-seq)
       (filter #(.isFile %))
       (map #(.getPath %))))

(defn functions [s]
  (->> (load-file s)
       (meta)
       :ns
       (str)
       (symbol)
       (ns-publics)))

(defn route-map? [m]
  (or (contains? m :get)
      (contains? m :post)
      (contains? m :put)
      (contains? m :patch)
      (contains? m :delete)))

(defn route [m]
  (let [{:keys [name ns middleware]} m
        route-map (select-keys m [:get :head :post :put :patch :delete])]
    (->> (mapv (fn [[k v]] [k v (-> (format "%s/%s" (str ns) (str name)) symbol resolve)]) route-map)
         (mapv #(wrap-route-with % middleware))
         (first))))

(defn make-routes [s]
  (as-> (functions s) %
        (vals %)
        (map meta %)
        (map #(select-keys % [:get :head :post :put :patch :delete :name :ns :middleware]) %)
        (filter route-map? %)
        (mapv route %)))

(defn routes [s]
  (->> (paths s)
       (map make-routes)
       (apply concat)
       (vec)))
