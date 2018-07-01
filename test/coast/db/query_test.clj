(ns coast.db.query-test
  (:require [coast.db.query :as q]
            [clojure.test :refer [deftest testing is]]))

(deftest sql-vec
  (testing "sql-vec with an and where clause"
    (is (= ["select member.name as member_name, member.email as member_email\nfrom member\nwhere member.name = ? and member.email = ?\nlimit 1" "test" "test@test.com"]
           (q/sql-vec
            (q/select :member/name :member/email)
            (q/where :member/name "test"
                     :member/email "test@test.com")
            (q/limit 1)))))

  (testing "sql-vec with an or where clause"
    (is (= ["select member.name as member_name, member.email as member_email\nfrom member\nwhere member.name = ? or member.email = ?\nlimit 1" "test" "test@test.com"]
           (q/sql-vec
            (q/select :member/name :member/email)
            (q/where (q/or :member/name "test"
                           :member/email "test@test.com"))
            (q/limit 1)))))

  (testing "a sql-vec that tries out most of the stuff"
    (is (= ["select member.id as member_id, member.name as member_name, member.email as member_email, todo.name as todo_name, todo.id as todo_id\nfrom member\nwhere member.id != ? and todo.name != ?\norder by todo.name desc, member.name asc\noffset 10\nlimit 1" 1 "hello"]
           (q/sql-vec
            (q/select :member/id :member/name :member/email :todo/name :todo/id)
            (q/where (q/not (q/and :member/id 1
                                   :todo/name "hello")))
            (q/limit 1)
            (q/offset 10)
            (q/order :todo/name :desc
                     :member/name)))))

  (testing "a join with a select statement that doesn't include the main table"
    (is (= ["select todo.name as todo_name\nfrom member\njoin todo on todo.member_id = member.id\nwhere todo.name is not null"]
           (q/sql-vec
             (q/select :todo/name)
             (q/joins :member/todos)
             (q/where (q/not (q/and :todo/name nil))))))))

(deftest from
  (testing "from with a select without the main table"
    (is (= "from member"
           (q/from [:todo/name] [:todo/member])))))
