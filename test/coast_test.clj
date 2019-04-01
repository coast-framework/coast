(ns coast-test
  (:require [coast]
            [clojure.test :refer [deftest testing is]]))

;(def app (coast/app {:routes routes}))

(defn get* [request]
  "get successful")

(defn post [request]
  "post successful")


(defn api-get [request]
  {:status "up"})

(defn api-post [request]
  (:body request))

(deftest vec-routes-test
  (let [routes {:routes [[:get "/" ::get*]
                         [:post "/" ::post]]
                :logger false}
        app (coast/app routes)]
    (testing "get from vector of routes"
        (is (= "get successful"
               (:body (app {:request-method :get :uri "/"})))))

    (testing "post from vector of routes"
        (is (= "post successful"
               (:body (app {:request-method :post :uri "/"})))))))


(deftest routes-test
  (let [routes {:routes (coast/routes
                         [:get "/" ::get*]
                         [:post "/" ::post])
                :logger false}
        app (coast/app routes)]
    (testing "get from routes"
      (is (= "get successful"
             (:body (app {:request-method :get :uri "/"})))))

    (testing "post from routes"
      (is (= "post successful"
             (:body (app {:request-method :post :uri "/"})))))))


(deftest site-routes-test
  (let [routes {:routes (coast/site
                         [:get "/" ::get*]
                         [:post "/" ::post])
                :logger false}
        app (coast/app routes)]
    (testing "get from site routes"
      (is (= "get successful"
             (:body (app {:request-method :get :uri "/"})))))

    (testing "post from site routes"
      (is (= 403
             (:status (app {:request-method :post :uri "/"})))))))


(deftest api-routes-test
  (let [routes {:routes (coast/api
                         (coast/with-prefix "/api"
                           [:get "/" ::api-get]
                           [:post "/" ::api-post]))
                :logger false}
        app (coast/app routes)]
    (testing "get from api routes"
      (is (= "{\"status\":\"up\"}"
             (:body (app {:request-method :get :uri "/api"})))))

    (testing "post from api routes"
      (is (= "[{\"a\":\"1\"},{\"b\":\"2\"}]"
             (:body (app {:request-method :post :uri "/api" :body "[{\"a\":\"1\"},{\"b\":\"2\"}]"})))))

    (testing "post from api routes with nil body"
      (is (= ""
             (:body (app {:request-method :post :uri "/api"})))))

    (testing "post from api routes with blank body"
      (is (= ""
             (:body (app {:request-method :post :uri "/api" :body ""})))))))


(deftest api-and-site-routes-test
  (let [routes {:routes (coast/routes
                         (coast/site
                           [:get "/" ::get*]
                           [:post "/" ::post])
                         (coast/api
                          (coast/with-prefix "/api"
                            [:get "/" ::api-get]
                            [:post "/" ::api-post])))
                :logger false}
        app (coast/app routes)]
    (testing "get from api routes"
      (is (= "{\"status\":\"up\"}"
             (:body (app {:request-method :get :uri "/api"})))))

    (testing "post from api routes"
      (is (= "[{\"a\":\"1\"},{\"b\":\"2\"}]"
             (:body (app {:request-method :post :uri "/api" :body "[{\"a\":\"1\"},{\"b\":\"2\"}]"})))))

    (testing "post from api routes with nil body"
      (is (= ""
             (:body (app {:request-method :post :uri "/api"})))))

    (testing "post from api routes with blank body"
      (is (= ""
             (:body (app {:request-method :post :uri "/api" :body ""})))))

    (testing "get from site routes"
      (is (= "get successful"
             (:body (app {:request-method :get :uri "/"})))))

    (testing "post from site routes"
      (is (= 403
             (:status (app {:request-method :post :uri "/"})))))))
