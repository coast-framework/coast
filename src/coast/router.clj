(ns coast.router
  (:require [clojure.string :as string]
            [coast.response :as response]))


(def app-routes (atom []))


(defn verb? [value]
  (contains? #{:get :post :put :patch :delete :head}
    value))


(defn route? [val]
  (and (vector? val)
       (verb? (first val))
       (string? (second val))
       (or (fn? (nth val 2))
           (keyword? (nth val 2)))))


(defn qualify-ident [k]
  (when (and (ident? k)
             (re-find #"-" (name k)))
    (let [[kns kn] (string/split (name k) #"-")]
      (keyword (or kns "") (or kn "")))))


(defn replacement [match m]
  (let [fallback (first match)
        k (-> match last keyword)
        s1 (get m k)
        s2 (get m (qualify-ident k))]
    (str (or s1 s2 fallback))))


(defn route-str [s m]
  (when (and (string? s)
             (or (nil? m) (map? m)))
    (string/replace s #":([\w-_]+)" #(replacement % m))))


(defn to-symbol [k]
  (if (keyword? k)
    (symbol (namespace k) (name k))
    k))


(defn resolve-safely [sym]
  (when (symbol? sym)
    (resolve sym)))


(defn resolve-route [k]
  (-> (to-symbol k)
      (resolve-safely)))


(defn params [s]
  (->> (re-seq #":([\w-_]+)" s)
       (map last)
       (map keyword)))


(defn pattern [s]
  (->> (string/replace s #":([\w-_]+)" "([A-Za-z0-9-_~]+)")
       (re-pattern)))


(defn route-params [req-uri route-uri]
  (when (every? string? [req-uri route-uri])
    (let [ks (params route-uri)
          param-pattern (pattern route-uri)]
      (->> (re-seq param-pattern req-uri)
           (first)
           (drop 1)
           (zipmap ks)))))


(defn match [request route]
  (when (route? route)
    (let [{:keys [request-method uri]} request
          [route-method route-uri] route
          params (route-params uri route-uri)
          route-uri (route-str route-uri params)]
      (and (= request-method route-method)
           (or (= uri route-uri)
               (= (str uri "/") route-uri))))))


(defn exact-match [request route]
  (let [{:keys [request-method uri]} request
        [route-method route-uri] route]
    (and (= request-method route-method)
         (= uri route-uri))))


(defn route [request routes]
  (or
   (-> (filter #(exact-match request %) routes)
       (first))
   (-> (filter #(match request %) routes)
       (first))))


(defn depth
  ([val]
   (depth val 0))
  ([val idx]
   (if (sequential? val)
     (depth (first val) (inc idx))
     idx)))


(defn flatten-wrapped-routes [x]
  (if (> (depth x) 1)
    (mapcat flatten-wrapped-routes x)
    [x]))


(defn middleware [& args]
  (let [fns (filter fn? args)
        vectors (->> (filter vector? args)
                     (flatten-wrapped-routes)
                     (vec))]
    (mapv #(vec (concat % fns)) vectors)))


(defn middleware-fn [route]
  (->> (drop 3 route)
       (apply comp)))


(defn ring-response [response]
  (if (vector? response)
    (response/render :html response :status :ok)
    response))


(defn simulated-methods [handler]
  (let [methods {"put" :put
                 "patch" :patch
                 "delete" :delete}]
    (fn [request]
      (let [method (if (= :post (:request-method request))
                     (get methods (get-in request [:params :_method]) (:request-method request))
                     (:request-method request))]
        (handler (assoc request :request-method method
                                :original-request-method (:request-method request)))))))


(defn app
  "Creates a ring handler from routes"
  [routes]
  (swap! app-routes #(-> (concat % routes) (distinct) (vec)))
  (fn [request]
    (let [{:keys [uri params]} request
          route (route request routes)
          [_ route-uri route-keyword] route
          route-handler (resolve-route route-keyword)
          route-params (route-params uri route-uri)
          route-middleware (middleware-fn route)
          request (assoc request :params (merge params route-params)
                                 :route route-keyword)
          handler (route-middleware route-handler)]
      (when (some? handler)
        (-> (handler request)
            (ring-response))))))


(defn routes [& routes]
  (vec (flatten-wrapped-routes routes)))


(defn url-encode [s]
  (when (string? s)
    (-> (java.net.URLEncoder/encode s "UTF-8")
        (.replace "+" "%20")
        (.replace "*" "%2A")
        (.replace "%7E" "~"))))


(defn query-string [m]
  (let [s (->> (map (fn [[k v]] (str (-> k name url-encode) "=" (url-encode v))) m)
               (string/join "&"))]
    (when (not (string/blank? s))
      (str "?" s))))


(defn url-for
  ([route-keyword]
   (url-for route-keyword {}))
  ([route-keyword params]
   (let [routes @app-routes
         route (-> (filter #(= (nth % 2) route-keyword) routes)
                   (first))
         url (route-str (nth route 1) params)
         route-params (route-params url (nth route 1))
         query-params (-> (apply dissoc params (keys route-params))
                          (dissoc :#))
         anchor (get params :#)
         anchor (if (some? anchor) (str "#" anchor) "")]
     (str url (query-string query-params) anchor))))


(defn action-for
  ([route-keyword]
   (action-for route-keyword {}))
  ([route-keyword params]
   (let [routes @app-routes
         [method route-url] (-> (filter #(= (nth % 2) route-keyword) routes)
                                (first))
         action (route-str route-url params)
         _method method
         method (if (not= :get method)
                  :post
                  :get)]
     {:method method
      :_method _method
      :action action})))


(defn redirect-to [& args]
  {:status 302
   :body ""
   :headers {"Location" (apply url-for args)}})


(defn prefix-route [s route]
  (if (>= (count route) 3)
    (update route 1 #(str s %))
    route))


(defn prefix-routes [s & routes]
  (if (and (= 1 (count routes))
           (every? route? (first routes)))
    (mapv #(prefix-route s %) (first routes))
    (mapv #(prefix-route s %) routes)))


(defn prefix [& args]
  (apply prefix-routes args))
