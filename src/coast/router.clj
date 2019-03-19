(ns coast.router
  (:require [clojure.string :as string]
            [coast.responses :as res]
            [coast.utils :as utils]
            [coast.error :as error]))

(def param-re #":([\w-_]+)")

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
    (string/replace s param-re #(replacement % m))))

(def verbs #{:get :post :put :patch :delete :head :resource :404 :500})

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

(defn route? [val]
  (and (vector? val)
       (contains? verbs (first val))))

(defn routes? [val]
  (every? route? val))

(defn match [request-route route]
  (when (route? route)
    (let [[request-method request-uri] request-route
          [route-method route-uri] route
          params (route-params request-uri route-uri)
          route-uri (route-str route-uri params)]
      (and (= request-method route-method)
           (or (= request-uri route-uri)
               (= (str request-uri "/") route-uri))))))

(defn server-error
  "Special route for 500s"
  [f]
  [:500 f])

(defn not-found
  "Special route for 404s"
  [f]
  [:404 f])

(defn prefix-param [s]
  (as-> (utils/singular s) %
        (str  % "-id")))


(defn resource-route [m]
  (let [{:keys [method route handler]} m]
    [method route handler]))

(defn resource
  "Creates CRUD route vectors

   Generates routes that look like this:

   [[:get    '/resources          :resource/index]
    [:get    '/resources/:id      :resource/view]
    [:get    '/resources/build    :resource/build]
    [:get    '/resources/:id/edit :resource/edit]
    [:post   '/resources          :resource/create]
    [:put    '/resources/:id      :resource/change]
    [:delete '/resources/:id      :resource/delete]]

   Examples:

   (resource :item)
   (resource :item :only [:create :delete])
   (resource :item :sub-item :only [:index :create])
   (resource :item :except [:index])
   "
  [routes & ks]
  (let [ks (if (not (vector? routes))
             (apply conj [routes] ks)
             (vec ks))
        routes (if (vector? routes)
                routes
                [])
        only? (and (not (empty? (filter #(= % :only) ks)))
                   (sequential? (last ks))
                   (not (empty? (last ks))))
        except? (and (not (empty? (filter #(= % :except) ks)))
                     (sequential? (last ks))
                     (not (empty? (last ks))))
        filter-resources (when (or only? except?) (last ks))
        route-ks (if (or only? except?)
                   (vec (take (- (count ks) 2) ks))
                   ks)
        resource-ks (take-last 1 route-ks)
        resource-names (map name resource-ks)
        resource-name (first resource-names)
        route-names (map utils/plural resource-names)
        prefix-ks (drop-last route-ks)
        route-str (as-> (map str prefix-ks) %
                        (map prefix-param %)
                        (concat '("") (interleave (map name prefix-ks) %) route-names)
                        (string/join "/" %))
        resources [{:method :get
                    :route route-str
                    :handler (keyword resource-name "index")
                    :name :index}
                   {:method :get
                    :route (str route-str "/build")
                    :handler (keyword resource-name "build")
                    :name :build}
                   {:method :get
                    :route (str route-str "/:" resource-name "-id")
                    :handler (keyword resource-name "view")
                    :name :view}
                   {:method :post
                    :route route-str
                    :handler (keyword resource-name "create")
                    :name :create}
                   {:method :get
                    :route (str route-str "/:" resource-name "-id/edit")
                    :handler (keyword resource-name "edit")
                    :name :edit}
                   {:method :put
                    :route (str route-str "/:" resource-name "-id")
                    :handler (keyword resource-name "change")
                    :name :change}
                   {:method :delete
                    :route (str route-str "/:" resource-name "-id")
                    :handler (keyword resource-name "delete")
                    :name :delete}]
        resources (if only?
                   (filter #(not= -1 (.indexOf filter-resources (clojure.core/get % :name))) resources)
                   resources)
        resources (if except?
                   (filter #(= -1 (.indexOf filter-resources (clojure.core/get % :name))) resources)
                   resources)
        resources (map resource-route resources)]
    (vec (concat routes resources))))


(defn slurp* [val]
  (when (some? val)
    (slurp val)))

(defn wrap-route
  "Wraps a single route in a ring middleware fn"
  [route middleware]
  (let [[method uri val route-name] route
        val (if (vector? val) val [val])
        new-val (if (sequential? middleware)
                   (apply conj val middleware)
                   (conj val middleware))]
    (->> [method uri new-val route-name]
         (filter some?)
         (vec))))

(defn resource-fmt [coll]
  (apply conj (vec (first coll))
              (rest coll)))

(defn resource-args [val]
  (if (or (= val '(:only))
          (= val '(:except)))
    (first val)
    val))

(defn resource-route? [route]
  (= :resource (first route)))

(defn expand-resource [route]
  (if (resource-route? route)
    (->> (partition-by #(contains? #{:only :except} %) route)
         (map resource-args)
         (resource-fmt)
         (drop 1)
         (apply resource))
    [route]))

(defn wrap-routes
  "Wraps a given set of routes in the given functions"
  [& args]
  (let [routes (->> (filter sequential? args)
                    (flatten)
                    (partition-by verb?)
                    (partition 2)
                    (mapv #(vec (apply concat %)))
                    (mapcat expand-resource)
                    (filter #(not (resource-route? %)))
                    (vec))
        fns (filter fn? args)]
    (mapv #(wrap-route % fns) routes)))

(defn fallback-not-found [_]
  (res/not-found
    [:html
      [:head
       [:title "Not Found"]]
      [:body
       [:h1 "404 Page not found"]]]))

(defn fallback-api-not-found [_]
  {:status 404
   :body {:status 404
          :message "404 uri not found"}
   :headers {"content-type" "application/json"}})

(defn resolve-route-fn [f]
  (cond
    (symbol? f) (resolve f)
    (keyword? f) (-> f utils/keyword->symbol utils/resolve-safely)
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

(defn not-found-fn [routes]
  (-> (filter #(= :404 (first %)) routes)
      (first)
      (nth 1)
      (utils/keyword->symbol)
      (utils/resolve-safely)))

(defn route-has-api-middleware? [route]
  (and (>= (count route) 3)
       (vector? (nth route 2))
       (->> (filter fn? (nth route 2))
            (map (fn [val] (str val)))
            (filter (fn [val] (string/starts-with? val "coast.middleware$api_routes")))
            (first)
            (some?))))

(defn api-not-found-fn [routes]
  (-> (filter #(and (= :404 (first %))
                    (route-has-api-middleware? %))
              routes)
      (first)
      (nth 1)
      (utils/keyword->symbol)
      (utils/resolve-safely)))

(defn server-error-fn [routes]
  (-> (filter #(= :500 (first %)) routes)
      (first)
      (nth 1)
      (utils/keyword->symbol)
      (utils/resolve-safely)))

(defn api-server-error-fn [routes]
  (-> (filter #(and (= :500 (first %))
                    (route-has-api-middleware? %))
              routes)
      (first)
      (nth 1)
      (utils/keyword->symbol)
      (utils/resolve-safely)))

(defn handler
  "Returns a ring handler from routes and any middleware"
  [routes opts]
  (fn [request]
    (let [{:keys [request-method uri params]} request
          route (->> (filter #(match [request-method uri] %) routes)
                     (first))
          [_ route-uri f] route
          route-params (route-params uri route-uri)
          route-handler (resolve-route f)
          _ (when (nil? route-handler)
              (error/raise (str "Route not found " uri) {:not-found f}))
          middleware (route-middleware-fn f)
          route-name (if (sequential? f) (first f) f)
          request (assoc request ::name route-name
                                 :coast/opts opts
                                 :params (merge params route-params))
          response (if (some? middleware)
                     ((middleware route-handler) request)
                     (route-handler request))]
      (cond
        (vector? response) {:status 200 :body response ::name route-name}
        (map? response) (assoc response ::name route-name)
        (string? response) {:status 200 :body response ::name route-name}
        :else (throw (Exception. "You can only return vectors, maps and strings from handler functions"))))))


(defn matches-route-identifier? [route k]
  (when (>= (count route) 3)
    (or (= k (last route))
        (= k (if (vector? (nth route 2))
               (first (nth route 2))
               (nth route 2))))))


(defn find-by-route-name [routes k]
  (-> (filter #(matches-route-identifier? % k) routes)
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
          (throw (Exception. (str "(url-for " k  ") is missing parameters. Check that the route parameters match the args passed in to url-for"))))
        (str url (query-string q-params)))
      (throw (Exception. "url-for takes a keyword and a map as arguments")))))

(defn action-for-routes [routes]
  "Returns a function that takes a route name and a optional map and returns a form map"
  (fn [k & [m]]
    (if (url-for-routes-args? k m)
      (let [[method route-url] (find-by-route-name routes (keyword k))
            action (route-str route-url m)
            _method method
            method (if (not= :get method)
                     :post
                     :get)]
        {:method method
         :_method _method
         :action action})
      (throw (Exception. "action-for takes a keyword and a map as arguments")))))

(defn pretty-route [route]
  (let [[method uri val route-name] route
        f (if (vector? val) (first val) val)
        middleware (if (vector? val)
                     (string/join
                      (map #(if-let [meta (-> % :meta)]
                              (name meta)
                              "")
                           val)
                      " ")
                     "")]
    (str (-> method name string/upper-case)
         " "
         uri
         " "
         f
         " "
         route-name
         " "
         middleware)))

(defn prefix-route [s route]
  (if (>= (count route) 3)
    (update route 1 #(str s %))
    route))

(defn prefix-routes [s & routes]
  (if (and (= 1 (count routes))
           (routes? (first routes)))
    (mapv #(prefix-route s %) (first routes))
    (mapv #(prefix-route s %) routes)))


(defn pretty-routes [routes]
  (println (string/join "\n" (map pretty-route routes))))


(defn recreate-middleware-fns [val]
  (let [[method url] val
        fns (filter fn? val)
        k (if (>= (count val) 3)
            (nth val 2)
            nil)
        route-name (if (and (some? k)
                            (not= (last val) k))
                     (last val)
                     nil)
        new-route [method url]
        new-route (if (empty? fns)
                    (conj new-route k)
                    (conj new-route (vec (concat [k] fns))))
        new-route (if (ident? route-name)
                    (conj new-route route-name)
                    new-route)]
    (vec
      (filter some? new-route))))


(defn routes [& args]
  (if (and (every? vector? args)
           (every? true? (->> (mapcat identity args)
                              (map route?))))
    (->> (apply concat args)
         (mapcat expand-resource)
         (filter #(not (resource-route? %)))
         (vec))
    (->> (filter sequential? args)
         (flatten)
         (partition-by verb?)
         (partition 2)
         (mapv #(vec (apply concat %)))
         (mapcat expand-resource)
         (filter #(not (resource-route? %)))
         (mapv recreate-middleware-fns)
         (vec))))
