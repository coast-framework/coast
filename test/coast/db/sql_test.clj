(ns coast.db.sql-test
  (:require [coast.db.sql :as sql]
            [clojure.test :refer [deftest testing is use-fixtures]]))


(deftest select-test
  (testing "a select statement with one column"
    (is (= ["select post.id as post$id from post"]
           (sql/sql-vec "sqlite" {} {} '[:select post/id
                                         :from post]
                                       {}))))

  (testing "a select statement with an asterisk"
    (is (= ["select id from post"]
           (sql/sql-vec "sqlite" {:post [:id]} {} '[:select *
                                                    :from post]
                                                   {}))))

  (testing "upsert"
    (is (= ["insert into post (title, hash) values (?, ?) on conflict (hash) do update set title = excluded.title, updated_at = ?" "title" "hash"]
           (drop-last (sql/sql-vec
                       "sqlite"
                       {:post [:title :hash]}
                       {}
                       '[:insert post/title post/hash
                         :values ["title" "hash"]
                         :on-conflict "hash"
                         :do-update-set [title "excluded.title"]]
                       {}))))))
