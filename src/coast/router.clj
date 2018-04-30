(ns coast.router
  (:require [clojure.string :as string]
            [clojure.edn :as edn]
            [coast.responses :as responses]
            [coast.utils :as utils]
            [clojure.repl :as repl]
            [clojure.pprint :refer [pprint]]
            [coast.env :as env])
  (:refer-clojure :exclude [get])
  (:import (java.time Duration)))

(def param-re #":([\w-_]+)")

(defn replacement [match m]
  (let [fallback (first match)
        k (-> match last keyword)]
    (str (clojure.core/get m k fallback))))

(defn route-str [s m]
  (when (and (string? s)
             (or (nil? m) (map? m)))
    (let [result (string/replace s param-re #(replacement % m))]
      (if (re-find #":" result)
        (throw (Exception. (str "The map given for route " s " is missing a parameter")))
        result))))

(def verbs #{:get :post :put :patch :delete :head})

(defn verb? [value]
  (contains? verbs value))

(defn method-verb? [value]
  (-> (disj verbs :get :post)
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

(defn route
  "Sugar for making a route vector"
  ([method routes uri f]
   (conj routes [method uri f]))
  ([method uri f]
   (route method [] uri f)))

(def get (partial route :get))
(def post (partial route :post))
(def put (partial route :put))
(def patch (partial route :patch))
(def delete (partial route :delete))

(defn wrap-route [route middleware]
  "Wraps a single route in a ring middleware fn"
  (let [[method uri val] route]
    (if (vector? val)
      [method uri (conj val middleware)]
      [method uri (conj [val] middleware)])))

(defn wrap-routes [routes middleware]
  "Wraps a given set of routes in a function."
  (map #(wrap-route % middleware) routes))

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
    [:html
      [:head
       [:title "Not Found"]]
      [:body
       [:h1 "404 Page not found"]]]))

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

(defn diff [start end]
  (let [duration (Duration/between start end)]
    (.toMillis duration)))

(defn req-method [request]
  (or (-> request :params :_method keyword) (:request-method request)))

(defn log-string [request]
  (let [{:keys [uri]} request
        uri (or uri "N/A")
        ;status (or (-> response :status) "N/A")
        method (-> (req-method request) name string/upper-case)]
    (utils/fill {:uri uri
                 :method method}
                (utils/long-str
                 ""
                 ""
                 "Request made to :method: :uri:"
                 (when (not (empty? (:params request)))
                   (utils/long-str ""
                                   "Parameters"
                                   (with-out-str (pprint (:params request)))))))))

(defn log [request]
  (when (= "dev" (env/env :coast-env))
    (println (log-string request))))

(defn match-routes [routes not-found-fn]
  "Turns routes into a ring handler"
  (fn [request]
    (let [{:keys [request-method uri params]} request
          method (or (-> params :_method keyword) request-method)
          route (-> (filter #(match [method uri] %) routes)
                    (first))
          [_ route-uri f] route
          route-params (route-params uri route-uri)
          params (merge params route-params)
          handler (resolve-route f not-found-fn)
          coerced-params (utils/map-vals coerce-params params)
          request (assoc request :params coerced-params
                                 ::params params)
          _ (log request)]
      (handler request))))

(defn resolve-keyword [k]
  (when (qualified-keyword? k)
    (let [n (name k)
          ns (namespace k)]
      (-> (symbol ns n)
          (resolve)))))

(defn resource-routes [m]
  (let [s (-> (vals m)
              (first)
              (namespace)
              (string/split #"\.") (last))
        {:strs [index show new edit create change delete]} m]
    (->> [[:get (format "/%s" s) (-> index resolve-keyword)]
          [:get (format "/%s/new" s) (-> new resolve-keyword)]
          [:get (format "/%s/:id" s) (-> show resolve-keyword)]
          [:get (format "/%s/:id/edit" s) (-> edit resolve-keyword)]
          [:post (format "/%s" s) (-> create resolve-keyword)]
          [:put (format "/%s/:id" s) (-> change resolve-keyword)]
          [:delete (format "/%s/:id" s) (-> delete resolve-keyword)]]
         (filterv #(-> % (last) (some?))))))

(defn resource
  "Creates a set of seven functions that map to a conventional set of named functions.
   Generates routes that look like this:
   [[:get    '/resources          resources/index]
    [:get    '/resources/:id      resources/show]
    [:get    '/resources/new      resources/new]
    [:get    '/resources/:id/edit resources/edit]
    [:post   '/resources          resources/create]
    [:put    '/resources/:id      resources/update]
    [:delete '/resources/:id      resources/delete]]
   Examples:
   (resource items/show items/index)
   (resource items/create items/delete)
   (resource items/index items/create)
   (resource items/index)
   (resource :items)
   "
  [& args]
  (let [functions (if (vector? (first args))
                    (rest args)
                    args)
        functions (if (and (= 1 (count functions))
                           (keyword? (first functions)))
                    (map #(->> (name %) (str (name (first functions)) "/" )) [:index :new :show :create :edit :update :delete])
                    functions)
        routes (->> (map #(-> % str repl/demunge (string/replace #"@\w+" "") keyword) functions)
                    (mapv #(vector (name %) %))
                    (into {})
                    (resource-routes))]
    (if (vector? (first args))
      (vec (concat (first args) routes))
      routes)))
