(ns coast-test
  (:require [coast]
            [clojure.test :refer [deftest testing is]]))


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


(deftest api-routes-test
  (let [routes (coast/routes
                (coast/prefix "/api"
                  [:get "/" ::api-get]
                  [:post "/" ::api-post]))
        app (-> (coast/app routes)
                (coast/json))]
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
  (let [site-routes (coast/routes
                      [:get "/" ::get*]
                      [:post "/" ::post])
        api-routes (coast/routes
                    (coast/prefix "/api"
                      [:get "/" ::api-get]
                      [:post "/" ::api-post]))
        site (-> (coast/app site-routes)
                 (coast/sessions)
                 (coast/body-parser))
        api (-> (coast/app api-routes)
                (coast/json))
        app (coast/apps api site)]
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


(deftest simulated-methods-test
  (let [app (coast/app {:routes [[:put "/" (fn [request] "i'm a put")]
                                 [:delete "/" (fn [request] "i'm a delete")]]
                        :logger false})]

    (testing "put request"
      (is (= "i'm a put"
             (:body (app {:request-method :put :uri "/"})))))

    (testing "delete request"
      (is (= "i'm a delete"
             (:body (app {:request-method :delete :uri "/"})))))))

(deftest url-for-test
  (let [routes (coast/routes
                 [:get "/" ::home]
                 [:post "/" ::home-action]
                 [:get "/hello" ::hello]
                 [:get "/hello/:id" ::hello-id])
        _ (coast/app routes)]
    (testing "url-for without a map"
      (is (= "/" (coast/url-for ::home))))

    (testing "url-for with a map with no url params"
      (is (= "/hello?key=value" (coast/url-for ::hello {:key "value"}))))

    (testing "url-for with a map with url params"
      (is (= "/hello/1?key=value" (coast/url-for ::hello-id {:id 1 :key "value"}))))

    (testing "url-for with a map, a url param and a #"
      (is (= "/hello/2?key=value#anchor" (coast/url-for ::hello-id {:id 2 :key "value" :# "anchor"}))))))
