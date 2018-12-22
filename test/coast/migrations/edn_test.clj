(ns coast.migrations.edn-test
  (:require [coast.migrations.edn :as migrations.edn]
            [clojure.test :refer :all]))

(deftest migrate
  (testing "migrate with user table"
    (with-redefs [migrations.edn/content (fn [s] [{:db/ident :user/name :db/type "text"}])]
      (is (thrown-with-msg? Exception #"user is a reserved word in postgres try a different name for this table"
                            (migrations.edn/migrate ""))))))
