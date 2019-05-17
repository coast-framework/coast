(ns coast.db.migrations-test
  (:require [coast.db.migrations :refer [references col create-table]]
            [coast.db.connection]
            [clojure.test :refer [deftest testing is]]))

(deftest ^:migrations col-test
  (testing "col with type and name but no options"
    (is (= "name text" (col :text "name" {}))))

  (testing "col with reference"
    (is (= "account integer not null references account(id) on delete cascade"
           (references :account)))))

(deftest ^:migrations create-table-test
  (with-redefs [coast.db.connection/spec (fn [_] "sqlite")]
    (testing "create-table with a table name"
      (is (= '("create table customer ( id integer primary key )")
             (create-table "customer"))))

    (testing "create-table with reference"
      (is (= '("create table customer ( id integer primary key, account integer not null references account(id) on delete cascade )" "create index customer_account_index on customer (account)")
             (create-table "customer"
              (references :account)))))))
