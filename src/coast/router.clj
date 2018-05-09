(ns coast.router
  (:require [clojure.string :as string]
            [clojure.edn :as edn]
            [coast.responses :as responses])
  (:refer-clojure :exclude [get]))

(def param-re #":([\w-_]+)")

(defn replacement [match m]
  (let [fallback (first match)
        k (-> match last keyword)]
    (str (clojure.core/get m k fallback))))

(defn route-str [s m]
  (when (and (string? s)
             (or (nil? m) (map? m)))
    (string/replace s param-re #(replacement % m))))

(def verbs #{:get :post :put :patch :delete :head})

(defn verb? [value]
  (contains? verbs value))

(defn method-verb? [value]
  (-> (disj verbs :get :post)
      (contains? value)))

(defn param-method [method]
  (when (method-verb? method)
    (str "?_method=" (name (or method "")))))

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
   (conj routes [method uri f (keyword f)]))
  ([method uri f]
   (route method [] uri f)))

(def get (partial route :get))
(def post (partial route :post))
(def put (partial route :put))
(def patch (partial route :patch))
(def delete (partial route :delete))

(defn wrap-route [route middleware]
  "Wraps a single route in a ring middleware fn"
  (let [[method uri val route-name] route]
    (if (vector? val)
      [method uri (conj val middleware) route-name]
      [method uri [val middleware] route-name])))

(defn wrap-routes [routes middleware]
  "Wraps a given set of routes in a function."
  (mapv #(wrap-route % middleware) routes))

(defn fallback-not-found-page [_]
  [:html
    [:head
     [:title "Not Found"]]
    [:body
     [:h1 "404 Page not found"]]])

(defn resolve-route-fn [f]
  (if (symbol? f)
    (resolve f)
    f))

(defn resolve-route [val]
  (if (vector? val)
    (-> (first val) (resolve-route-fn))
    (resolve-route-fn val)))

(defn route-middleware-fn [val]
  (when (vector? val)
    (->> (rest val)
         (map resolve-route-fn)
         (apply comp))))

(defn route-name [route]
  (-> route last keyword))

(defn handler [not-found-page]
  (fn [request]
    (let [route-handler (::route-handler request)]
      (if (nil? route-handler)
        (responses/not-found
          ((or not-found-page fallback-not-found-page) request))
        (route-handler request)))))

(defn wrap-route-info [handler routes]
  "Adds route info to request map"
  (fn [request]
    (let [{:keys [request-method uri params]} request
          method (or (-> params :_method keyword) request-method)
          route (-> (filter #(match [method uri] %) routes)
                    (first))
          [_ route-uri f] route
          route-params (route-params uri route-uri)
          route-handler (resolve-route f)
          request (assoc request ::route-handler route-handler
                                 :route/middleware (route-middleware-fn f)
                                 :route/name (if (vector? f) (first f) f)
                                 :params (merge params route-params))]
      (handler request))))

(defn resource-routes
  ([symbols]
   (let [routes [[:get "/%s" "index"]
                 [:get "/%s/new" "new"]
                 [:get "/%s/:id" "show"]
                 [:get "/%s/:id/edit" "edit"]
                 [:post "/%s" "create"]
                 [:put "/%s/:id" "update"]
                 [:delete "/%s/:id" "delete"]]
         names (->> (map name symbols)
                    (set))]
     (if (and (not (nil? symbols))
              (not (empty? symbols))
              (every? symbol? symbols))
       (filter #(contains? names (last %)) routes)
       routes)))
  ([]
   (resource-routes nil)))

(defn resource-route [prefix route-ns route]
  (let [[method s name] route]
    [method (format s prefix) (symbol route-ns name) (keyword prefix name)]))

(defn routes? [args]
  (coll? (first args)))

(defn resource
  "Creates a set of seven functions that map to a conventional set of named functions.
   Generates routes that look like this:
   [[:get    \"/resources\"          `resources/index]
    [:get    \"/resources/:id\"      `resources/show]
    [:get    \"/resources/new\"      `resources/new]
    [:get    \"/resources/:id/edit\" `resources/edit]
    [:post   \"/resources\"          `resources/create]
    [:put    \"/resources/:id\"      `resources/update]
    [:delete \"/resources/:id\"      `resources/delete]]
   Examples:
   (resource `items/show `items/index)
   (resource `items/create `items/delete)
   (resource `items/index `items/create)
   (resource `items/index)
   (resource :items)"
  [& args]
  (let [all-routes (first args)
        args (if (routes? args)
               (rest args)
               args)
        prefix (if (keyword? (first args))
                 (name (first args))
                 (string/replace (namespace (first args)) #"controllers\." ""))
        route-ns (if (keyword? (first args))
                   (str "controllers." prefix)
                   (namespace (first args)))
        routes (map #(resource-route prefix route-ns %)
                    (resource-routes args))]
    (if (coll? all-routes)
      (reduce conj all-routes routes)
      (vec routes))))

(defn find-by-route-name [routes k]
  (-> (filter #(= k (route-name %)) routes)
      (first)))

(defn url-for-routes-args? [k m]
  (and (keyword? k)
       (or (nil? m) (map? m))))

(defn url-for-routes [routes]
  "Returns a function that takes a route name and a map and returns a route as a string"
  (fn [k & [m]]
    (if (url-for-routes-args? k m)
      (let [[method route-url] (find-by-route-name routes k)
            url (route-str route-url m)]
        (when (nil? url)
          (throw (Exception. (str "The route with name " k " doesn't exist. Try adding it to your routes"))))
        (when (re-find #":" url)
          (throw (Exception. (str "The map given for route " k " is missing a parameter"))))
        (str url (param-method method)))
      (throw (Exception. "url-for takes a keyword and a map as arguments")))))

(defn action-for-routes [routes]
  "Returns a function that takes a route name and a optional map and returns a form map"
  (fn [k & [m]]
    (if (url-for-routes-args? k m)
      (let [[method route-url] (find-by-route-name routes k)
            action (str (route-str route-url m)
                        (param-method method))
            method (if (not= :get method)
                     :post
                     :get)]
        {:method method
         :action action})
      (throw (Exception. "action-for takes a keyword and a map as arguments")))))
