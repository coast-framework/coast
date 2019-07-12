(ns coast.db.helpers-test
  (:require [coast.db.helpers :as helpers]
            [clojure.test :refer [deftest testing is]]))


(deftest ^:coast.db.helpers insert-test
  (testing "insert with nested map"
    (is (= [:insert :account/a :account/b :values '(1 2)] (helpers/insert {:account {:a 1 :b 2}}))))

  (testing "insert with qualified keyword map"
    (is (= [:insert :account/a :account/b :values '(1 2)] (helpers/insert #:account{:a 1 :b 2}))))

  (testing "insert with dot keyword map"
    (is (= [:insert :account/a :account/b :values '(1 2)] (helpers/insert {:account.a 1 :account.b 2})))))


(deftest ^:coast.db.helpers insert-all-test
  (testing "insert with nested map"
    (is (= [:insert :account/a :account/b :values '(1 2) '(3 4)] (helpers/insert-all {:account [{:a 1 :b 2}
                                                                                                {:a 3 :b 4}]}))))

  (testing "insert with qualified keyword map"
    (is (= [:insert :account/a :account/b :values '(1 2) '(3 4)] (helpers/insert-all [#:account{:a 1 :b 2}
                                                                                      #:account{:a 3 :b 4}]))))

  (testing "insert with dot keyword map"
    (is (= [:insert :account/a :account/b :values '(1 2) '(3 4)] (helpers/insert-all [{:account.a 1 :account.b 2}
                                                                                      {:account.a 3 :account.b 4}])))))
