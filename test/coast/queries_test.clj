(ns coast.queries-test
  (:require [coast.queries :as queries]
            [clojure.test :refer [deftest testing is]]))

(deftest sql-ks
  (testing "insert"
    (is (= [:created_at] (queries/sql-ks "insert into (created_at) values (:created_at::date)"))))

  (testing "update"
    (is (= [:name :id] (queries/sql-ks "update table set name = :name where id = :id"))))

  (testing "delete"
    (is (= [:id] (queries/sql-ks "delete from table where id = :id"))))

  (testing "select"
    (is (= [:id :slug] (queries/sql-ks "select * from table where id = :id and slug = :slug")))))
