(ns coast.router
  (:require [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [coast.responses :as responses]))

(def param-re #":([\w-_]+)")

(defn replacement [match m]
  (let [fallback (first match)
        k (-> match last keyword)]
    (str (get m k fallback))))

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

(defn url-encode [s]
  (when (string? s)
    (-> (java.net.URLEncoder/encode s "UTF-8")
        (.replace "+" "%20")
        (.replace "*" "%2A")
        (.replace "%7E" "~"))))

(defn unqualified-keyword? [k]
  (and (keyword? k)
       (nil? (namespace k))))

(defn query-string [m]
  (when (and (map? m)
             (every? string? (vals m))
             (every? unqualified-keyword? (keys m)))
    (let [s (->> (map (fn [[k v]] (str (-> k name url-encode) "=" (url-encode v))) m)
                 (string/join "&"))]
      (when (not (string/blank? s))
        (str "?" s)))))

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

(defn match [request-route route]
  (let [[request-method request-uri] request-route
        [route-method route-uri] route
        params (route-params request-uri route-uri)]
    (and (= request-method route-method)
         (= request-uri (route-str route-uri params)))))

(defn expand-keyword-route [k]
  (when (and (keyword? k)
             (not (qualified-keyword? k)))
    (let [s (name k)]
      [[:get (str "/" s "/index") (keyword (str s ".index") "view")]
       [:get (str "/" s "/:id") (keyword (str s ".show") "view")]
       [:get (str "/new-" s) (keyword (str s ".new") "view")]
       [:post (str "/new-" s) (keyword (str s ".new") "action")]
       [:get (str "/edit-" s "/:id") (keyword (str s ".edit") "view")]
       [:post (str "/edit-" s "/:id") (keyword (str s ".edit") "action")]
       [:post (str "/delete-" s "/:id") (keyword (str s ".delete") "action")]])))

(defn expand-qualified-keyword-route [k]
  (when (qualified-keyword? k)
    (let [s (name k)
          p (namespace k)
          m {"index" [[:get (str "/" p "/index") (keyword (str p ".index") "view")]]
             "show" [[:get (str "/" p "/:id") (keyword (str p ".show") "view")]]
             "new" [[:get (str "/new-" p) (keyword (str p ".new") "view")]
                    [:post (str "/new-" p) (keyword (str p ".new") "action")]]
             "edit" [[:get (str "/edit-" p "/:id") (keyword (str p ".edit") "view")]
                     [:post (str "/edit-" p "/:id") (keyword (str p ".edit") "action")]]
             "delete" [[:post (str "/delete-" p "/:id") (keyword (str p ".delete") "action")]]}]
      (get m s))))

(defn expand-route [v]
  (let [[k] v]
    (if (not (verb? k))
      (if (qualified-keyword? k)
        (map #(conj % (last v)) (expand-qualified-keyword-route k))
        (map #(conj % (last v)) (expand-keyword-route k)))
      [v])))

(defn slurp* [val]
  (when (some? val)
    (slurp val)))

(defn wrap-route
  "Wraps a single route in a ring middleware fn"
  [route middleware]
  (let [[method uri val route-name] route
        val (if (vector? val) val [val])]
    [method
     uri
     (if (sequential? middleware)
       (apply conj val middleware)
       (conj val middleware))
     (or route-name (-> val first keyword))]))

(defn wrap-routes
  "Wraps a given set of routes in the given functions"
  [& args]
  (let [routes (last args)
        fns (drop-last args)]
    (vec
     (mapcat identity
             (mapv (fn [route]
                     (mapv (fn [mw]
                             (wrap-route route mw)) fns))
                   routes)))))

(defn fallback-not-found-page [_]
  (responses/not-found
    [:html
      [:head
       [:title "Not Found"]]
      [:body
       [:h1 "404 Page not found"]]]))

(defn keyword->symbol [k]
  (let [kns (namespace k)
        kn (name k)]
    (symbol kns kn)))

(defn resolve-route-fn [f]
  (cond
    (symbol? f) (resolve f)
    (keyword? f) (-> f keyword->symbol resolve)
    :else f))

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
    (let [route-handler (or (:route/handler request) not-found-page fallback-not-found-page)]
      (route-handler request))))

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
          request (assoc request :route/handler route-handler
                                 :route/middleware (route-middleware-fn f)
                                 :route/name (if (vector? f) (first f) f)
                                 :params (merge params route-params))]
      (handler request))))

(defn find-by-route-name [routes k]
  (-> (filter #(= k (route-name %)) routes)
      (first)))

(defn url-for-routes-args? [k m]
  (and (ident? k)
       (or (nil? m) (map? m))))

(defn url-for-routes [routes]
  "Returns a function that takes a route name and a map and returns a route as a string"
  (fn [k & [m]]
    (if (url-for-routes-args? k m)
      (let [[method route-url] (find-by-route-name routes (keyword k))
            url (route-str route-url m)
            r-params (route-params url route-url)
            q-params (apply dissoc m (keys r-params))
            q-params (if (method-verb? method)
                       (assoc q-params :method method)
                       q-params)]
        (when (nil? url)
          (throw (Exception. (str "The route with name " k " doesn't exist. Try adding it to your routes"))))
        (when (re-find #":" url)
          (throw (Exception. (str "The map " m " used for route " k " (" url ") is missing parameters"))))
        (str url (query-string q-params)))
      (throw (Exception. "url-for takes a keyword and a map as arguments")))))

(defn action-for-routes [routes]
  "Returns a function that takes a route name and a optional map and returns a form map"
  (fn [k & [m]]
    (if (url-for-routes-args? k m)
      (let [[method route-url] (find-by-route-name routes (keyword k))
            action (str (route-str route-url m)
                        (param-method method))
            method (if (not= :get method)
                     :post
                     :get)]
        {:method method
         :action action})
      (throw (Exception. "action-for takes a keyword and a map as arguments")))))

(defn pretty-route [route]
  (let [[method uri val route-name] route
        f (if (vector? val) (first val) val)]
    (str (-> method name string/upper-case)
         " "
         uri
         " "
         f
         " "
         route-name)))

(defn prefix-route [s route]
  (update route 1 #(str s %)))

(defn prefix-routes [s routes]
  (mapv #(prefix-route s %) routes))

(defn pretty-routes [routes]
  (println (string/join "\n" (map pretty-route routes))))
