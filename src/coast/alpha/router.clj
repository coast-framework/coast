(ns coast.alpha.router
  (:require [clojure.string :as string]
            [word.core :as word]
            [clojure.edn :as edn]
            [hiccup.page]
            [hiccup.core]
            [coast.alpha.responses :as responses]
            [coast.utils :as utils])
  (:refer-clojure :exclude [get]))

(def param-re #":([\w-_]+)")

(defn replacement [match m]
  (let [default (first match)
        k (-> match last keyword)]
    (str (clojure.core/get m k default))))

(defn route-str [s m]
  (string/replace s param-re #(replacement % m)))

(def verbs [:get :post :put :patch :delete])

(defn verb? [value]
  (utils/in? value verbs))

(defn method-verb? [value]
  (let [method-verbs (-> (drop 1 verbs)
                         (vec))]
    (utils/in? value method-verbs)))

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
  "Sugar for making a trail vector"
  ([method routes uri f]
   (conj routes [method uri f]))
  ([method uri f]
   (route method [] uri f)))

(def get (partial route :get))
(def post (partial route :post))
(def put (partial route :put))
(def patch (partial route :patch))
(def delete (partial route :delete))

(defn wrap-route-with [route middleware]
  "Wraps a single route in a ring middleware fn"
  (let [[method uri f] route]
    [method uri (middleware f)]))

(defn wrap-routes-with [routes middleware]
  "Wraps a given set of routes in a function."
  (map #(wrap-route-with % middleware) routes))

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

(defn wrap-coerce-params [handler]
  "Coerces integers and uuid values in params"
  (fn [request]
    (let [{:keys [params]} request
          request (assoc request :params (utils/map-vals coerce-params params))]
      (handler request))))

(defn default-not-found-fn []
  (responses/not-found
    (hiccup.page/html5
      [:head
       [:title "Not Found"]]
      [:body
       [:h1 "404 Page not found"]])))

(defn match-routes [routes not-found-fn]
  "Turns trail routes into a ring handler"
  (fn [request]
    (let [{:keys [request-method uri params]} request
          method (or (-> params :_method keyword) request-method)
          route (-> (filter #(match [method uri] %) routes)
                    (first))
          [_ route-uri handler] route
          trail-params (route-params uri route-uri)
          params (merge params trail-params)
          handler (or handler not-found-fn default-not-found-fn)
          params (utils/map-vals coerce-params params)
          request (assoc request :params params)]
      (handler request))))

(defn wrap-match-routes [arg not-found-fn]
  (if (fn? arg)
    (fn [request]
      (arg request))
    (match-routes arg not-found-fn)))

(defn prefix-param [s]
  (as-> (word/singular s) %
        (str  % "-id")))

(defn resource-route [m]
  (let [{:keys [method route handler]} m]
    [method route (-> handler symbol resolve)]))

(defn resource
  "Creates a set of seven functions that map to a conventional set of named functions.
   Generates routes that look like this:

   [[:get    '/resources          resources/index]
    [:get    '/resources/:id      resources/show]
    [:get    '/resources/fresh    resources/fresh] ; this is 'fresh' not 'new' because new is reserved
    [:get    '/resources/:id/edit resources/edit]
    [:post   '/resources          resources/create]
    [:put    '/resources/:id      resources/change] ; this is 'change' not 'update' because update is in clojure.core
    [:delete '/resources/:id      resources/delete]]

   Examples:

   (resource :items)
   (resource :items :only [:create :delete])
   (resource :items :sub-items :only [:index :create])
   (resource :items :except [:index])
   "
  [routes & ks]
  (let [ks (if (not (vector? routes))
             (apply conj [routes] ks)
             (vec ks))
        routes (if (vector? routes)
                 routes
                 [])
        only? (and (not (empty? (filter #(= % :only) ks)))
                   (vector? (last ks))
                   (not (empty? (last ks))))
        except? (and (not (empty? (filter #(= % :except) ks)))
                     (vector? (last ks))
                     (not (empty? (last ks))))
        filter-resources (when (or only? except?) (last ks))
        route-ks (if (or only? except?)
                   (vec (take (- (count ks) 2) ks))
                   ks)
        resource-ks (take-last 1 route-ks)
        resource-names (map name resource-ks)
        resource-name (first resource-names)
        prefix-ks (drop-last route-ks)
        route-str (as-> (map str prefix-ks) %
                        (map prefix-param %)
                        (concat '("") (interleave (map name prefix-ks) %) resource-names)
                        (string/join "/" %))
        resources [{:method :get
                    :route route-str
                    :handler (str resource-name "/index")
                    :name :index}
                   {:method :get
                    :route (str route-str "/fresh")
                    :handler (str resource-name "/fresh")
                    :name :fresh}
                   {:method :get
                    :route (str route-str "/:id")
                    :handler (str resource-name "/show")
                    :name :show}
                   {:method :post
                    :route route-str
                    :handler (str resource-name "/create")
                    :name :create}
                   {:method :get
                    :route (str route-str "/:id/edit")
                    :handler (str resource-name "/edit")
                    :name :edit}
                   {:method :put
                    :route (str route-str "/:id")
                    :handler (str resource-name "/change")
                    :name :change}
                   {:method :delete
                    :route (str route-str "/:id")
                    :handler (str resource-name "/delete")
                    :name :delete}]
        resources (if only?
                    (filter #(not= -1 (.indexOf filter-resources (clojure.core/get % :name))) resources)
                    resources)
        resources (if except?
                    (filter #(= -1 (.indexOf filter-resources (clojure.core/get % :name))) resources)
                    resources)
        resources (map resource-route resources)]
    (vec (concat routes resources))))
