# coast on clojure

The easy full stack clojure web framework

```clojure
{coast.alpha {:git/url "https://github.com/swlkr/coast"
              :sha "4539e148bea1212c403418ec9dfbb2d68a0db3d8"}}`
```

Previously: [alpha](https://github.com/swlkr/coast/tree/4539e148bea1212c403418ec9dfbb2d68a0db3d8), [0.6.8](https://github.com/swlkr/coast/tree/0.6.8)

### Warning
The current version is under construction, but you can use it anyway 😅

## Table of Contents

- [Quickstart](#quickstart)
- [Shipping](#shipping)
- [Database](#database)
- [Models](#models)
- [Views](#views)
- [Controllers](#controllers)
- [Helpers](#helpers)

## Quickstart

Create a new coast project like this
```bash
brew install clojure
curl -o /usr/local/bin/coast https://raw.githubusercontent.com/swlkr/coast/master/coast
chmod a+x /usr/local/bin/coast
coast new blog
```

Let's set up the database!
```bash
make db/create # assumes a running postgres server. creates a new db called blog_dev
```

Let's create a table to store posts and generate some code to so we can interact with that table!
```bash
coast gen migration create-posts title:text body:text
make db/migrate
coast gen mvc posts
```

Let's see our masterpiece so far

```bash
make nrepl
```

Then in your editor, send this to your repl on port 7888

```clojure
(coast) ; => Listening on port 1337
```

You should be greeted with the text "You're coasting on clojure!"
when you visit `http://localhost:1337` and when you visit `http://localhost:1337/posts`
you should be able to add, edit, view and delete the rows from the post table!

## Shipping

```bash
make uberjar
make db/migrate
make server
```

## Database

The only currently supported database is postgres. PRs gladly accepted to add more.

There are a few generators to help you get the boilerplate-y database stuff out of the way:

```bash
make db/create
make db/drop
make db/migrate
make db/rollback
coast gen migration
```

Those all do what you think they do.

#### `make db/create`

The database name is your project name underscore dev. So
if your project name is `cryptokitties`, your db would be named `cryptokitties_dev`. This assumes a running
postgres server and the process running leiningen has permission to create databases. I don't know what happens
when those requirements aren't met. Probably an error of some kind. You can actually change this from `project.clj`
since the aliases have the name in them as the first argument.

#### `make db/drop`

The opposite of `db/create`. Again, I don't know what happens when you run drop before create. Probably an error.

#### `coast gen migration`

This creates a migration which is just plain sql 😎 with a timestamp and the filename in the `resources/migrations` folder

#### `coast gen migration the-name-of-a-migration`

This creates an empty migration that looks like this

```sql
-- up

-- down

```

#### `coast gen migration create-posts`

This creates a migration that creates a new table with the "default coast"
columns of id and created_at.

```sql
-- up
create table posts (
  id serial primary key,
  created_at timestamp without time zone default (now() at time zone 'utc')
)

-- down
drop table posts
```

#### `coast gen migration create-posts title:text body:text`

This makes a new migration that creates a table with the given name:type
columns.

```sql
-- up
create table posts (
  id serial primary key,
  title text,
  body text,
  created_at timestamp without time zone default (now() at time zone 'utc')
)

-- down
drop table posts
```

#### `make db/migrate`

This performs the migration

## Models

Models are clojure functions that call external sql files in `resources/sql` There's a generator for the basic crud operations:

#### `coast gen model posts`

This requires that the posts table already exists and it creates three files:

1. A sql file named `resources/sql/posts.db.sql`
3. A clojure file named `src/db/posts.clj`
2. Another clojure file named `src/models/posts.clj`

The model file, the db file and the sql file all work together to make your life better:

Here's `posts.db.sql`. Each query is separated by a newline and `-- name`.
They all have a `-- name` which comes after the colon. These values can be any string that can be resolved to a clojure function.
They also optionally have a function which runs after the results are received from the database, and this functions operates on all rows
at once, not just row by row, luckily, clojure still works and you can use `map` for row by row function application.

```sql
-- name: list
select *
from posts
order by created_at
limit = :limit
offset = :offset


-- name: find
-- fn: first
select *
from posts
where id = :id


-- name: insert
-- fn: first
insert into posts (
  title,
  body
)
values (
  :title,
  :body
)
returning *


-- name: update
-- fn: first
update posts
set
  title = :title,
  body = :body
where id = :id
returning *


-- name: delete
-- fn: first
delete from posts
where id = :id
returning *

```

Here is where the `db.clj` file comes in and references the `posts.db.sql` file

```clojure
(ns db.posts
  (:require [coast.alpha :refer [defq]])
  (:refer-clojure :exclude [update list find]))

(defq list "sql/posts.db.sql")
(defq find "sql/posts.db.sql")
(defq insert "sql/posts.db.sql")
(defq update "sql/posts.db.sql")
(defq delete "sql/posts.db.sql")
```

`defq` is a macro that reads the sql resource at compile time and generates functions
with the symbols of the names in the sql resource. If you try to specify a name that doesn't have a corresponding `-- name:`
in the sql resource, you'll get a compile exception, so that's kind of cool. I know this part is kind of boilerplate-y but luckily
this gets generated for you.

The idea behind the `.db.sql` naming is that this sql file is special and not manually edited, so it can be regenerated at any time with
new schema changes for insert/update queries. You can of course create any number of .sql files you want, so if you needed to customize
posts with comment counts or something similar, you could do this in `posts.sql`, not `posts.db.sql`:

```sql
-- name: posts-with-count
select
  posts.*,
  p.comments
from
  posts
join
  (
    select
      comments.post_id,
      count(comments.id) as comments
    from
      comments
    where
      comments.post_id = :id
    group by
      comments.post_id
 ) p on p.post_id = posts.id
 ```

 Then in the db file:

```clojure
(defq posts-with-count "sql/posts.sql")
```

And now you have a new function wired to a bit of custom sql.

The last part of the this three part process is the model file in a different namespace so the function names can be reused and called from the controllers:

```clojure
(ns models.posts
  (:require [db.posts :as db.posts]
            [coast.utils :as utils])
  (:refer-clojure :exclude [list find update]))

(def columns [:title :body])

(defn id [request]
  (-> (:params request)
      (select-keys [:id])))

(defn params [request]
  (-> (:params request)
      (select-keys columns)))

(defn list [request]
  (->> (:params request)
       (db.posts/list)
       (assoc request :posts)))

(defn find [request]
  (->> (id request)
       (db.posts/find)
       (assoc request :post)))

(defn insert [request]
  (->> (params request)
       (db.posts/insert)
       (assoc request :post)))

(defn update [request]
  (let [post (-> (id request)
                 (db.posts/find))]
    (->> (params request)
         (merge post)
         (db.posts/update)
         (assoc request :post))))

(defn delete [request]
  (->> (params request)
       (db.posts/delete)
       (assoc request :post)))
```

There's a lot to the models, but quite a bit less than something like active record.

## Views

Views are clojure functions the emit hiccup with a few implicit things related to re-usable layouts
When starting up a coast app, you call `coast/app` and you can pass in a few options, one of which
is a function that represents a persistent set of html elements you want to see across all pages
of your site

```clojure
(ns server
  (:require [coast.alpha :as coast]
            [coast.components :as c]))

(def app (coast/app {:layout c/layout}))
```

When you return a vector from any view function, it automatically gets rendered as html and rendered inside
of the layout function where you tell it to, the default layout function looks like this:

```clojure
(ns components
  (:require [coast.alpha :as coast]))

(defn layout [request body]
  (coast/html5
    [:head
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     (coast/include-css "/css/app.css")]
    [:body
     body
     (coast/include-js "/js/app.js")]))
```

So this returns a vector and then there's the coast middleware that turns a hiccup vector and string responses into ring response maps:

```clojure
(defn layout? [response layout]
  (and (not (nil? layout))
       (or (vector? response)
           (string? response))))

(defn wrap-layout [handler layout]
  (fn [request]
    (let [response (handler request)]
      (cond
        (map? response) response
        (layout? response layout) (responses/ok (layout request response))
        :else (responses/ok response)))))
```

which you can override by returning your own response map, that's pretty much all there is to views. The goal
is to do a clojure-y thing by combining small functions in the components namespace into the components of your app,
text fields, select fields, forms, lists, headers, "cards", "panels", similar to bootstrap but hopefully re-usable.
I also recommend functional css or atomic css libraries like [tachyons](http://tachyons.io) or basscss.

## TODO

- Document views
- Document controllers
- Document routes
- Document auth
- Document logging?
- Document ... just more documentation

## Why did I do this?

In my short web programming career, I've found two things
that I really like, clojure and rails. This is my attempt
to put the two together.

## Credits

This framework is only possible because of the hard work of
a ton of great clojure devs who graciously open sourced their
projects that took a metric ton of hard work. Here's the list
of open source projects that coast uses:

- [potemkin](https://github.com/ztellman/potemkin)
- [http-kit](https://github.com/http-kit/http-kit)
- [hiccup](https://github.com/weavejester/hiccup)
- [ring/ring-core](https://github.com/ring-clojure/ring)
- [ring/ring-defaults](https://github.com/ring-clojure/ring-defaults)
- [ring/ring-devel](https://github.com/ring-clojure/ring)
- [org.postgresql/postgresql](https://github.com/pgjdbc/pgjdbc)
- [org.clojure/java.jdbc](https://github.com/clojure/java.jdbc)
- [org.clojure/tools.namespace](https://github.com/clojure/tools.namespace)
- [prone](https://github.com/magnars/prone)
- [verily](https://github.com/jkk/verily)
