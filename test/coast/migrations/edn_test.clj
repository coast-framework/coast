(ns coast.migrations.edn-test
  (:require [coast.migrations.edn :as migrations.edn]
            [clojure.test :refer :all]))

(deftest migrate-test
  (testing "migrate with user table"
    (is (thrown-with-msg? Exception #"user is a reserved word in postgres try a different name for this table"
                          (migrations.edn/migrate [{:db/ident :user/name :db/type "text"}])))))
