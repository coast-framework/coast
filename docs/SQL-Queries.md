# SQL Queries

## `defq`

`defq` is a macro that reads a sql file located in `resources` at compile time and generates functions
with the symbols of the names in the sql file. If you try to specify a name that doesn't have a corresponding `-- name:`
in the sql resource, you'll get a compile exception, so that's kind of cool.

Here's some SQL in a sql file: `resources/sql/posts.sql`

```sql
-- name: fetch
-- fn: first!
select *
from posts
where posts.id = :id
limit 1

-- name: all
select *
from posts

-- name: insert
insert into posts (
  title,
  body
) values (
  :title,
  :body
)

-- name: update
update posts
set title = :title,
    body = :body
where posts.id = :id

-- name: delete
delete from posts where id = :id
```

Here's a clojure function named `db.posts`

```clojure
(ns db.posts
  (:require [coast.db :refer [defq]]))

(defq "sql/posts.generated.sql")
```

This generates functions `fetch`, `all`, `insert`, `update` and `delete` in the `db.posts` namespace.

Here's another example:

```sql
-- name: posts-with-count
select
  posts.*,
  c.comment_count
from
  posts
join
  (
    select
      comments.post_id,
      count(comments.id) as comment_count
    from
      comments
    where
      comments.post_id = :post_id
    group by
      comments.post_id
 ) c on c.post_id = posts.id
 ```

 Then in the db file:

```clojure
(ns db.posts
  (:require [coast.db :refer [defq]]))

(defq posts-with-count "sql/posts.sql")

(posts-with-count {:post-id 1}) ; => [{:id 1 ... :comment-count 12}]
```

And now you have a new function wired to a bit of custom sql.
