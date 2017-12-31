# coast on clojure

The easy full stack clojure web framework

Current version: `[coast "0.5.3"]`

## Table of Contents

- [Quickstart](#quickstart)
- [Database](#database)
- [Models](#models)
- [Views](#views)
- [Controllers](#controllers)
- [Routes](#routes)
- [Helpers](#helpers)

## Quickstart

Create a new coast project like this
```bash
lein new coast blog
cd blog
```

Let's set up the database!
```bash
lein db/create # assumes a running postgres server. creates a new db called blog_dev
```

Let's create a table to store posts and generate some code to so we can interact with that table!
```bash
lein db/migration posts title:text body:text
lein db/migrate
lein mvc/gen posts
```

Go ahead and add the routes too, this will probably be generated in the future as well
```clojure
(ns blog.routes
  (:require [coast.core :as coast]
            [blog.controllers.home-controller :as home]
            [blog.controllers.errors-controller :as errors]
            [blog.controllers.posts-controller :as posts]))

(def routes
  (-> (coast/get "/" home/index)
      (coast/resource :posts)
      (coast/route-not-found errors/not-found)
      (coast/wrap-routes-with coast/wrap-coerce-params)))
```

Let's see our masterpiece so far

```clojure
lein repl ; or start a repl your preferred way
(coast) ; => Listening on port 1337
```

OR

```bash
lein run
```

You should be greeted with the text "You're coasting on clojure!"
when you visit http://localhost:1337 and when you visit http://localhost:1337/posts
you should be able to add, edit, view and delete the rows from the post table!

Amazing!

## Database

The only currently supported database is postgres. PRs gladly accepted to add more, the db abstraction is
jdbc along with a sql library I wrote [oksql](https://github.com/swlkr/oksql).

There are a few generators in the form of lein aliases to help you get the boilerplate-y database stuff out of the way:

```bash
lein db/create
lein db/drop
lein db/migration
lein db/migrate
```

Those all pretty much do what you think they do.

#### `lein db/create`

The database name is your project name underscore dev. So
if your project name is `cryptokitties`, your db would be named `cryptokitties_dev`. This assumes a running
postgres server and the process running leiningen has permission to create databases. I don't know what happens
when those requirements aren't met. Probably an error of some kind. You can actually change this from `project.clj`
since the aliases have the name in them as the first argument.

#### `lein db/drop`

The opposite of `db/create`. Again, I don't know what happens when you run drop before create. Probably an error.

#### `lein db/migration`

This creates a migration which is just plain sql 😎 with a timestamp and the filename in the `resources/migrations` folder

#### `lein db/migration the-name-of-a-migration`

This creates an empty migration that looks like this

```sql
-- up

-- down

```

#### `lein db/migration create-posts`

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

#### `lein db/migration create-posts title:text body:text`

This makes a new migration that creates a table with the given name:type
columns. Juicy.

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

#### `lein db/migrate`

This performs the migration, if one of them fails, it should stop migrating the rest.

## Models

Models are just clojure functions that call external sql files in `resources/sql` There's a generator for the basic crud operations:

#### `lein model/gen posts`

This requires that the posts table already exists and it creates two files:

1. A sql file named `resources/sql/posts.sql`
2. A model file named `src/models/posts.clj`

The model file and the sql file work together for sql queries and the where clause on updates and deletes.
Here's an example of how they work together.

Here's `posts.sql`. Each query is separated by a newline and `-- name`.
They all have a `-- name` which comes after the colon. These values can be any string that can be resolved to a clojure function.
They also optionally have a function which runs after the results are received from the database, and this functions operates on all rows
at once, not just row by row, luckily, clojure still works and you can use `map` for row by row function application.

```sql
-- name: all
select *
from posts
order by created_at desc

-- name: find-by-id
-- fn: first
select *
from posts
where id = :id

-- name: where
where id = :id
returning *
```

Here is where clojure comes in and references the `posts.sql` file

```clojure
(ns blog.models.posts
  (:require [coast.db :as db])
  (:refer-clojure :exclude [update]))

(def columns [:title :body])

 ; the keyword namespace is the file where the query is located, and the name corresponds to -- name: all
(defn all []
  (db/query :posts/all))

; same thing here
(defn find-by-id [id]
  (db/query :posts/find-by-id {:id id}))

; insert does not reference the sql file at all, it dynamically generates the sql
(defn insert [m]
  (->> (select-keys m columns)
       (db/insert :posts)))

; db/update and db/delete both reference the :file/where name which can be anything or you can have multiple
; queries with multiple where's if you want to
(defn update [id m]
  (as-> (select-keys m columns) %
        (db/update :posts % :posts/where {:id id})))

; same thing here, it's :table :file/name where name is where in this case
; could easily be

; (db/delete :posts :posts/delete-where {:id id}) or anything you can imagine

(defn delete [id]
  (db/delete :posts :posts/where {:id id}))
```

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
- [trail](https://github.com/swlkr/trail) (this one is a swlkr special)
- [trek](https://github.com/swlkr/trek) (so is this one)
- [bunyan](https://github.com/swlkr/bunyan) (and this one. although I guess real logging is coming... maybe)
- [environ](https://github.com/weavejester/environ)
- [hiccup](https://github.com/weavejester/hiccup)
- [ring/ring-core](https://github.com/ring-clojure/ring)
- [ring/ring-defaults](https://github.com/ring-clojure/ring-defaults)
- [ring/ring-devel](https://github.com/ring-clojure/ring)
- [org.postgresql/postgresql](https://github.com/pgjdbc/pgjdbc)
- [org.clojure/java.jdbc](https://github.com/clojure/java.jdbc)
- [org.clojure/tools.namespace](https://github.com/clojure/tools.namespace)
- [oksql](https://github.com/swlkr/oksql) (another swlkr special)
- [selmer](https://github.com/yogthos/Selmer)
- [inflections](https://github.com/r0man/inflections-clj)
- [prone](https://github.com/magnars/prone)
- [com.jakemccrary/reload](https://github.com/jakemcc/reload)
- [cheshire](https://github.com/dakrone/cheshire)
