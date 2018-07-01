# coast on clojure

The easy way to make websites with clojure

```clojure
coast.delta {:git/url "https://github.com/swlkr/coast"
             :sha "0e9913f1c609bfb8b391300810f742390e9b6028"}}
```

Previously: [gamma](https://github.com/swlkr/coast/tree/e2a0cacf25dd05b041d7b098e5db0a93592d3dea), [beta](https://github.com/swlkr/coast/tree/8a92be4a4efd5d4ed419b39ba747780f2de44fe4), [alpha](https://github.com/swlkr/coast/tree/4539e148bea1212c403418ec9dfbb2d68a0db3d8), [0.6.9](https://github.com/swlkr/coast/tree/0.6.9)

### Warning
The current version is under construction, but you can use it anyway 😅

## Table of Contents

- [Simple Quickstart](#quickstart-without-a-template)
- [Quickstart](#quickstart)
- [Shipping](#shipping)
- [Routing](#routing)
- [Database](#database)
- [Models](#models)
- [Views](#views)
- [Controllers](#controllers)
- [Helpers](#helpers)
- [Errors](#errors)

## Quickstart without a template

```bash
brew install clojure

mkdir -p blog blog/src
touch blog/deps.edn blog/src/server.clj
echo '{:paths ["src"] :deps {coast.delta {:git/url "https://github.com/swlkr/coast" :sha "0e9913f1c609bfb8b391300810f742390e9b6028"}}}' >> blog/deps.edn
```

It only takes a few lines to get up and running, add this to `src/server.clj`

```clojure
; blog/src/server.clj
(ns server
  (:require [coast.delta :as coast]
            [coast.prod.server :as prod.server]))

(defn hello [req]
  (str "hello " (-> req :params :name)))

(def routes [[:get "/hello/:name" `hello]])

(def app (coast/app routes))

(defn -main [& args]
  (prod.server/start app)) ; => starts listening on port 1337 by default
```

Usually you would use a REPL from your editor to start the server
but you can start it from your terminal with clj

```bash
clj -m server # => Server is listening on port 1337
```

If you visit `http://localhost:1337/hello/world`
you should see "hello world" printed out

```bash
curl http://localhost:1337/hello/world # => hello world
curl http://localhost:1337/hello/you # => hello you
curl http://localhost:1337/hello/goodbye # => hello goodbye
```

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

Let's create a table to store blog posts and generate some code so we can create, read, update and delete things in that table!
```bash
coast gen migration create-posts title:text body:text
make db/migrate
coast gen mvc posts
```

Can't forget the routes

```clojure
; src/routes.clj
(ns routes
  (:require [coast.router :as router])
  (:refer-clojure :exclude [get]))

(def routes (-> (router/get "/" `controllers.home/index)
                (router/resource :posts)))

(def url-for (router/url-for-routes routes))
(def action-for (router/action-for-routes routes))
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
you should be able to add, edit, view and delete the rows from the `posts` table!

## Shipping

```bash
make uberjar
make db/migrate
make server
```

## Routing

Routing in the Clojure world has seen its fair share of implementations. Coast’s implementation is based loosely on [nav](https://github.com/taylorlapeyre/nav/blob/master/README.md) and to some extent [pedestal](http://pedestal.io) and [compojure](https://github.com/weavejester/compojure).

Here’s a few examples of routes in coast
```clojure
[:get "/" `home]

[:put "/posts/:id" `posts/update]

[:delete "/posts/:post-id/comments/:id" `comments/delete]

; you can override the route name if you'd like
; otherwise the route name will be :posts/create
[:post "/posts" `posts/create :create-post]

; here's a more realistic "coast-y" example
[:post "/posts" `controllers.posts/create] ; => route name is :controllers.posts/create

; generally: [method route-string `function route-name]
```

If you don’t care to write vectors all day you can also use the helper functions in coast.router

```clojure
(ns routes
  (:require [coast.router :refer [get post put delete]))

(def routes (-> (get "/" `home/index)
                (get "/posts" `posts/index)
                (get "/posts/:id" `posts/show)
                (post "/posts" `posts/create)))
```

The thread first macro is used to conj everything into a set so the result is this:

```clojure
[[:get "/" `home/index]
 [:get "/posts" `posts/index]
 [:get "/posts/:id" `posts/show]
 [:post "/posts" `posts/create]}
```

Resource routing works like this:

```clojure
(def routes (-> (resource :posts)
                (resource `comments/index `comments/show)))
```

The first line there indicates you want all 7 crud routes, the second line says you only want index and show. Here are list of the crud routes available:

```clojure
[[:get    "/resources"          `resources/index]
 [:get    "/resources/new"      `resources/new]
 [:get    "/resources/:id"      `resources/show]
 [:get    "/resources/:id/edit" `resources/edit]
 [:post   "/resources"          `resources/create]
 [:put    "/resources/:id"      `resources/update]
 [:delete "/resources/:id"      `resources/delete]
```

You can wrap your routes in standard ring middleware if you need
to interact with the request map before the route functions get called

```clojure
(def routes (-> (get "/" `home/index)
                (middleware/wrap-auth)
                (middleware/coerce-params)))
```

So that’s how routing works in coast on clojure

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
  created_at timestamptz default now()
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
  created_at timestamptz default now()
)

-- down
drop table posts
```

#### `make db/migrate`

This performs the migration

## Models

Models are clojure functions that do one of two things, either call a `.sql` file in `resources` with the `defq` macro or they call one of the five
functions generated by the `defm` function. You can generate model functions just like migrations.

#### `coast gen model posts`

This requires that the posts table already exists and it creates two files that work together to make your life easier, `src/db/posts.clj` and `src/models/posts.clj`.

Here's what the `db/posts.clj` file looks like

```clojure
(ns db.posts
  (:require [coast.delta :refer [defm]])
  (:refer-clojure :exclude [update find]))

(defm "posts")
```

#### `defm`

`defm` is a function that generates a few sql helper functions in the current namespace:

- `find`
- `find-by`
- `find-or-create-by`
- `insert`
- `update`
- `delete`
- `query`

Here's an example of how each function created by `defm` can be used

#### `find`

```clojure
(db.posts/find 1)
; (-> ["select * from posts where id = ? limit 1" 1] first)
```

#### `find-by`

```clojure
(db.posts/find-by {:category "fun" :tag-count 0})
; (-> ["select * from posts where category = ? and tag_count = ?" "fun" 0] first)
```

#### `find-or-create-by`

```clojure
(db.posts/find-or-create-by {:category "fun" :tag-count 0})
; (-> ["select * from posts where category = ? and tag_count = ?" "fun" 0] first)
; or
; ["insert into posts (category, tag_count) values (?, ?) returning *" "fun" 0]
```

#### `insert`

```clojure
(db.posts/insert {:title "title" :body "body"})
; => ["insert into posts (title, body) values (?, ?) returning *" "title" "body"]
```

#### `update`

```clojure
(db.posts/update {:id 1 :title "new title"})
; => ["update posts set title = ? where id = ? returning *" "new title" 1]

(db.posts/update {:title "newest title"} ["title = ?" "new title"])
; => ["update posts set title = ? where title = ? returning *" "newest title" "new title"]
```

#### `delete`

```clojure
(db.posts/delete {:id 1})
; => ["delete from posts where id = ? returning *" 1]

(db.posts/delete ["title = ?" "new title"])
; => ["delete from posts where title = ? returning *" "new title"]
```

#### `query`

```clojure
(db.posts/query {:where {:title "newest title"}
                 :order [:created-at :desc]})
; => ["select * from posts where title = ? order by created_at desc" "newest title"]

(db.posts/query)
; => ["select * from posts"]

(db.posts/query {:order [:created-at :desc]})
; => ["select * from posts order by created_at desc"]

(db.posts/query {:select [:title]})
; => ["select posts.title from posts"]

(db.posts/query {:select [:id :title :published-at :created-at]
                 :where {:published-at nil}
                 :order [:created-at :desc]})
; ["select posts.id, posts.title, posts.published_at, posts.created_at
;   from posts
;   where posts.published_at is null
;   order by created_at desc"]
```

So those cover basic sql things that you really don't want to have to type out *every* time. For more complex sql things,
why not just use the real deal? I'm talking about SQL. I'm a firm believer in having an escape hatch when an abstraction gets too leaky, thank goodness there's `defq`.

#### `defq`

`defq` is a macro that reads a sql file located in `resources` at compile time and generates functions
with the symbols of the names in the sql file. If you try to specify a name that doesn't have a corresponding `-- name:`
in the sql resource, you'll get a compile exception, so that's kind of cool.

You can create any number of .sql files you want, so if you needed to customize
posts and join with comment counts or something similar, you could do this in `posts.sql`

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
(defq posts-with-count "sql/posts.sql")

(posts-with-count {:post-id 1}) ; => [{:id 1 ... :comment-count 12}]
```

And now you have a new function wired to a bit of custom sql.

The last part of the this process is the model file in a different namespace so the function names can be reused and called from the controllers:

```clojure
(ns models.posts
  (:require [db.posts]
            [coast.models])
  (:refer-clojure :exclude [find update]))

(defn validate [m]
  (let [validations []]
    (if (empty? validations)
      m
      (coast.models/validate validations m))))


(defn all []
  (db.posts/query))

(defn find [id]
  (db.posts/find id))

(defn create [m]
  (-> (validate m)
      (db.posts/insert)))

(defn update [m]
  (-> (validate m)
      (db.posts/update)))

(defn delete [m]
  (db.posts/delete))
```

Of course you don't even have to use these functions until you need them, it's perfectly ok to just call functions from the `db` namespace
and then insert validation and custom business logic in the model file when you need it.

## Views

Views are clojure functions the emit [hiccup](https://github.com/weavejester/hiccup) with a few implicit things related to re-usable layouts
When starting up a coast app, you call `coast/app` and you can pass in a few options, one of which
is a function that represents a persistent set of html elements you want to see across all pages
of your site

```clojure
(ns server
  (:require [coast.delta :as coast]))

(defn home [request]
  [:h1 "Welcome!"])

(def routes [[:get "/" `home]})

(defn layout [request body]
  [:html
    [:head
      [:title "Hello!"]
    [:body
      body]]])

(def app (coast/app routes {:layout layout}))

(app {:request-method :get :uri "/"}) ; => <html><head><title>Hello!</title></head><body><h1>Welcome!</h1></body></html>
```

If you want to return something else other than [hiccup](https://github.com/weavejester/hiccup), like a string or json, you can override coast's regular behavior of rendering html like this:

```clojure
(ns server
  (:require [coast.delta :as coast]
            [coast.responses.json :as res]))

(defn home [request]
  (res/ok {:message "Welcome!"}))

(def routes [[:get "/" `home]])

(def app (coast/app routes))

(app {:request-method :get :uri "/"}) ; => {:message "Welcome!"}
```

## Errors

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

## TODO

- Better error documentation
- Document validations
- Document controllers
- Document ... just more documentation

## Why did I do this?

In my short web programming career, I've found two things
that I really like, clojure and rails. This is my attempt
to put the two together.

## Credits

This framework is only possible because of the hard work of
a ton of great clojure devs who graciously open sourced their
projects.

Here's the list of open source projects that coast uses:

- [http-kit](https://github.com/http-kit/http-kit)
- [hiccup](https://github.com/weavejester/hiccup)
- [ring/ring-core](https://github.com/ring-clojure/ring)
- [ring/ring-defaults](https://github.com/ring-clojure/ring-defaults)
- [ring/ring-devel](https://github.com/ring-clojure/ring)
- [org.postgresql/postgresql](https://github.com/pgjdbc/pgjdbc)
- [org.clojure/java.jdbc](https://github.com/clojure/java.jdbc)
- [org.clojure/tools.namespace](https://github.com/clojure/tools.namespace)
- [verily](https://github.com/jkk/verily)
- [reload](https://github.com/jakemcc/reload)
