(ns coast.eta-test
  (:require [coast.eta :as coast]
            [clojure.test :refer [deftest testing is]]))

(def routes [[:get "/" ::home]
             [:post "/" ::home-action]
             [:get "/hello" ::hello]
             [:get "/hello/:id" ::hello-id]])

(def app (coast/app {:routes routes}))

(deftest url-for-test
  (testing "url-for without a map"
    (is (= "/" (coast/url-for ::home))))

  (testing "url-for with a map with no url params"
    (is (= "/hello?key=value" (coast/url-for ::hello {:key "value"}))))

  (testing "url-for with a map with url params"
    (is (= "/hello/1?key=value" (coast/url-for ::hello-id {:id 1 :key "value"}))))

  (testing "url-for with a map, a url param and a #"
    (is (= "/hello/2?key=value#anchor" (coast/url-for ::hello-id {:id 2 :key "value" :# "anchor"})))))
