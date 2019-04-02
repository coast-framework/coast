(ns coast.theta-test
  (:require [coast.theta :as coast]
            [coast.router :as router]
            [coast.middleware :as middleware]
            [clojure.test :refer [deftest testing is]]))


(deftest url-for-test
  (let [routes (router/routes
                 (coast.middleware/site-routes
                   [:get "/" ::home]
                   [:post "/" ::home-action]
                   [:get "/hello" ::hello]
                   [:get "/hello/:id" ::hello-id]))
        _ (coast/app {:routes routes})]
    (testing "url-for without a map"
      (is (= "/" (coast/url-for ::home))))

    (testing "url-for with a map with no url params"
      (is (= "/hello?key=value" (coast/url-for ::hello {:key "value"}))))

    (testing "url-for with a map with url params"
      (is (= "/hello/1?key=value" (coast/url-for ::hello-id {:id 1 :key "value"}))))

    (testing "url-for with a map, a url param and a #"
      (is (= "/hello/2?key=value#anchor" (coast/url-for ::hello-id {:id 2 :key "value" :# "anchor"}))))))
