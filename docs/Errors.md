# Errors

There are two types of errors. Exceptions and "coast errors" which are just `clojure.lang.ExceptionInfo`'s: `(throw (ex-info "" {:type :invalid}))`

Here's a good example of why coast separates these two: missing records from the database. The first thing that happens after a missing database record? A 404 page! That's exactly what coast does if you call either of these two functions:

```clojure
(db.posts/find 1) ; 404 is thrown, 404 page is shown
```

or

```sql
-- name: find
-- fn: first!
select *
from your_table
where your_table.id = :id
limit 1
```
