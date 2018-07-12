# Models

Models are clojure functions that do one this, either call a `.sql` file in `resources` with the `defq` macro. You can generate model functions and sql just like migrations.

## `coast gen model posts`

This requires that the posts table already exists and it creates three files that work together to make your life easier, `resources/sql/posts.generated.sql`, `src/db/posts.clj` and `src/models/posts.clj`.

Here's what the generated `posts.generated.sql` file looks like

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

Here's what the `db/posts.clj` file looks like

```clojure
(ns db.posts
  (:require [coast.db :refer [defq]]))

(defq "sql/posts.generated.sql")
```

## `defq`

`defq` is a macro that reads a sql file located in `resources` at compile time and generates functions
with the symbols of the names in the sql file. If you try to specify a name that doesn't have a corresponding `-- name:`
in the sql resource, you'll get a compile exception, so that's kind of cool.

You can create any number of .sql files you want, so if you needed to customize
posts and join with comment counts or something similar, you could do this in a new file `posts.sql` and regenerate `posts.generated.sql` when there are new columns added.

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

And now you have a new function wired to a bit of custom sql. Here's an example of using the db function from a model namespace

```clojure
(ns models.posts
  (:require [db.posts]))

(defn posts-with-count [i]
  (if (pos-int? i)
    (db.posts/posts-with-count {:post-id i})
    []))
```

Of course you don't even have to use these functions until you need them, it's perfectly ok to just call functions from the `db` namespace
and then insert validation and custom business logic in the model file when you need it.
