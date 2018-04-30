(ns coast.queries-test
  (:require [coast.queries :as queries]
            [clojure.test :refer [deftest testing is]]))

(deftest sql-ks-test
  (testing "insert"
    (is (= [:created_at] (queries/sql-ks "insert into (created_at) values (:created_at)"))))

  (testing "update"
    (is (= [:name :id] (queries/sql-ks "update table set name = :name where id = :id"))))

  (testing "delete"
    (is (= [:id] (queries/sql-ks "delete from table where id = :id"))))

  (testing "select"
    (is (= [:id :slug] (queries/sql-ks "select *, created_at::date from table where id = :id and slug = :slug")))))

(deftest parameterize-test
  (testing "insert"
    (is (= "insert into (id, created_at) values (?, ?)" (queries/parameterize "insert into (id, created_at) values (:id, :created_at)" {:id 1 :created_at 123}))))

  (testing "update"
    (is (= "update table set name = ? where id = ?" (queries/parameterize "update table set name = :name where id = :id" {:id 1 :name "hello"}))))

  (testing "in clause"
    (is (= "in (?,?,?)" (queries/parameterize "in (:list)" {:list [1 2 3]})))))

(deftest sql-vec-test
  (testing "insert"
    (is (= ["insert into (id, created_at) values (?, ?)" 1 2] (queries/sql-vec "insert into (id, created_at) values (:id, :created_at)" {:id 1 :created-at 2}))))

  (testing "update"
    (is (= ["update table set name = ? where id = ?" 1 2] (queries/sql-vec "update table set name = :name where id = :id" {:name 1 :id 2})))))
