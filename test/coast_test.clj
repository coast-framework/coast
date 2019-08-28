(ns coast-test
  (:require [coast]
            [clojure.test :refer [deftest testing is]]))


(deftest vec-routes-test
  (let [get* (fn [request] (coast/render :text "get successful"))
        post (fn [request] (coast/render :text "post successful"))
        routes [[:get "/" get*]
                [:post "/" post]]
        app (coast/app routes)]
    (testing "get from vector of routes"
        (is (= "get successful"
               (:body (app {:request-method :get :uri "/"})))))

    (testing "post from vector of routes"
        (is (= "post successful"
               (:body (app {:request-method :post :uri "/"})))))))


(deftest routes-test
  (let [get* (fn [request] (coast/render :text "get successful"))
        post (fn [request] (coast/render :text "post successful"))
        routes (coast/routes
                [:get "/" get*]
                [:post "/" post])
        app (coast/app routes)]
    (testing "get from routes"
      (is (= "get successful"
             (:body (app {:request-method :get :uri "/"})))))

    (testing "post from routes"
      (is (= "post successful"
             (:body (app {:request-method :post :uri "/"})))))))


(defn to-bytes [s]
  (byte-array (count s) (map (comp byte int) s)))


(deftest api-routes-test
  (let [api-get (fn [request] (coast/render :json {:status "up"}))
        api-post (fn [request] (coast/render :json (:body request)))
        routes (coast/routes
                (coast/prefix "/api"
                  [:get "/" api-get]
                  [:post "/" api-post]))
        app (-> (coast/app routes)
                (coast/json))]

    (testing "get from api routes"
      (is (= "{\"status\":\"up\"}"
             (:body (app {:request-method :get
                          :uri "/api"
                          :headers {"Content-Type" "application/json"}})))))

    (testing "post from api routes"
      (is (= "[{\"a\":\"1\"},{\"b\":\"2\"}]"
             (:body (app {:request-method :post
                          :uri "/api"
                          :headers {"Content-Type" "application/json"}
                          :body (to-bytes "[{\"a\":\"1\"},{\"b\":\"2\"}]")})))))

    (testing "post from api routes with nil body"
      (is (nil?
             (:body (app {:request-method :post
                          :uri "/api"
                          :headers {"Content-Type" "application/json"}
                          :body nil})))))

    (testing "post from api routes with blank body"
      (is (nil?
             (:body (app {:request-method :post
                          :uri "/api"
                          :headers {"Content-Type" "application/json"}
                          :body (to-bytes "")})))))))


; (deftest api-and-site-routes-test
;   (let [site-routes (coast/routes
;                       [:get "/" ::get*]
;                       [:post "/" ::post])
;         api-routes (coast/routes
;                     (coast/prefix "/api"
;                       [:get "/" ::api-get]
;                       [:post "/" ::api-post]))
;         site (-> (coast/app site-routes)
;                  (coast/sessions)
;                  (coast/body-parser))
;         api (-> (coast/app api-routes)
;                 (coast/json))
;         app (coast/apps api site)]
;
;     (testing "get from api routes"
;       (is (= "{\"status\":\"up\"}"
;              (:body (app {:request-method :get
;                           :headers {"Content-Type" "application/json"}
;                           :uri "/api"})))))
;
;     (testing "post from api routes"
;       (is (= "[{\"a\":\"1\"},{\"b\":\"2\"}]"
;              (:body (app {:request-method :post
;                           :uri "/api"
;                           :headers {"Content-Type" "application/json"}
;                           :body (to-bytes "[{\"a\":\"1\"},{\"b\":\"2\"}]")})))))
;
;     (testing "post from api routes with nil body"
;       (is (= "null"
;              (:body (app {:request-method :post
;                           :headers {"Content-Type" "application/json"}
;                           :uri "/api"})))))
;
;     (testing "post from api routes with blank body"
;       (is (= "null"
;              (:body (app {:request-method :post
;                           :uri "/api"
;                           :headers {"Content-Type" "application/json"}
;                           :body (to-bytes "")})))))
;
;     (testing "get from site routes"
;       (is (= "get successful"
;              (:body (app {:request-method :get
;                           :uri "/"})))))
;
;     (testing "post from site routes"
;       (is (= 403
;              (:status (app {:request-method :post :uri "/"})))))))


(deftest simulated-methods-test
  (let [routes (coast/routes
                [:put "/" (fn [request] (coast/render :text "i'm a put"))]
                [:patch "/" (fn [request] (coast/render :text "i'm a patch"))]
                [:delete "/" (fn [request] (coast/render :text "i'm a delete"))])
        app (-> (coast/app routes)
                (coast/simulated-methods))]
    (testing "put request"
      (is (= "i'm a put"
             (:body (app {:request-method :put :uri "/"})))))

    (testing "delete request"
      (is (= "i'm a delete"
             (:body (app {:request-method :delete :uri "/"})))))

    (testing "patch request"
      (is (= "i'm a patch"
             (:body (app {:request-method :patch :uri "/"})))))))
