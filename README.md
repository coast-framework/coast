## What is this?

Coast is a full stack web framework written in Clojure for small teams or solo developers. It uses a relational database and renders html on the server without javascript which allows you to ship your web applications faster

```clojure
(ns my-project
  (:require [coast]))

(def routes [[:get "/" :home]])

(defn home [req]
  [:h1 "You're coasting on clojure!"])

(def app (coast/app {:routes routes}))

(coast/server app {:port 1337})
```

## Getting Started

### Installation on Mac

1. Make sure clojure is installed first

```bash
brew install clojure
```

2. Install the coast cli script

```bash
curl -o /usr/local/bin/coast https://raw.githubusercontent.com/coast-framework/coast/master/coast && chmod a+x /usr/local/bin/coast
```

3. Create a new coast project

```bash
coast new myapp && cd myapp
```

### Installation on Linux (Debian/Ubuntu)

1. Make sure you have bash, curl, rlwrap, and Java installed

```bash
curl -O https://download.clojure.org/install/linux-install-1.9.0.391.sh
chmod +x linux-install-1.9.0.391.sh
sudo ./linux-install-1.9.0.391.sh
```

2. Install the coast cli script

```bash
sudo curl -o /usr/local/bin/coast https://raw.githubusercontent.com/coast-framework/coast/master/coast && sudo chmod a+x /usr/local/bin/coast
```

3. Create a new coast project

```bash
coast new myapp && cd myapp
```

You should be greeted with the text "You're coasting on clojure!"
when you visit `http://localhost:1337`

## Quickstart

This doc will take you from a fresh coast installation to a working todo list.

### New Project

The first thing you do when you start a coast project? `coast new` in your terminal:

```bash
coast new todos
```

This will make a new folder in the current directory named "todos". Let's get in there and see what's up:

```bash
cd todos
tree .
```

This will show you the layout of a default coast project:

```bash
.
├── Makefile
├── README.md
├── bin
│   └── repl.clj
├── deps.edn
├── resources
│   ├── assets.edn
│   └── public
│       ├── css
│       │   └── app.css
│       └── js
│           └── app.js
├── src
│   ├── components.clj
│   ├── error
│   │   ├── not_found.clj
│   │   └── server_error.clj
│   ├── home
│   │   └── index.clj
│   ├── routes.clj
│   └── server.clj
└── test
    └── server_test.clj

9 directories, 14 file
```

### Databases

For the sake of this tutorial, we want to show a list of todos as the first thing people see when they come to our site. In coast, that means making a place for these todos to live, in this case (and in every case): the database. Assuming your postgres server is up and running, you can make a database with a handy shortcut that coast gives you:

```bash
make db/create
# clj -A\:db/create
# Database todos_dev created successfully
```

This will create a database with the name of your project and whatever `COAST_ENV` is set to, which by default is `dev`. So the database name will be `todos_dev`.

### Migrations

Now that the database is created, let's generate a migration:

```bash
coast gen migration add-todos
# resources/migrations/20180926190239_add_todos.edn created
```

This will create a file in `resources/migrations` with a timestamp and whatever name you gave it, in this case: `add_todos`. Let's fill it in with our first migration:

```clojure
[{:db/col :todo/name
  :db/type "text"}

 {:db/col :todo/completed-at
  :db/type "timestamptz"}]
```

This is edn, not sql, although sql migrations would work, in coast it's cooler if you use edn migrations for the sweet query power you'll have later. The left side of the `/` is the name of the table, and the right side is the name of the column. Or in coast terms: `:<resource>/<prop>`  Let's apply this migration to the database

```bash
make db/migrate
# clj -A\:db/migrate
#
# -- Migrating:  20180926160239_add_todos ---------------------
#
# [#:db{:col :todo/name, :type text} #:db{:col :todo/completed-at, :type timestamptz}]
#
# -- 20180926160239_add_todos ---------------------
#
# 20180926160239_add_todos migrated successfully
```

This updates the database and creates a `resources/schema.edn` file to keep track of relationships and things that don't normally fit in the database schema.

### Generators

Now that the database has been migrated and we have a place to store the todos we want to show them too. This is where coast generators come in. Rather than you having to type everything out, generators are a way to get you started and you can customize from there.

This will create a file in the `src` directory with the name of an `action`. Coast is a pretty humble web framework, there's no FRP or graph query languages or anything. There are just five actions: `create`, `read`, `update`, `delete`, and `list`. You can specify an action to generate or you can generate all five. Lets just generate the list file for now:

```bash
coast gen action todo:list
# src/todo/list.clj created successfully
```

This is specifying which resource (table) to generate and it puts a file in `src/todo/list.clj` which looks like this:

```clojure
(ns todo.list
  (:require [coast :refer [pull q url-for validate]]))

(defn view [request]
  (let [rows (q '[:pull [:todo/name :todo/completed-at]])]
    [:table
     [:thead
      [:tr
       [:th "name"]
       [:th "completed-at"]]]
     [:tbody
       (for [row rows]
        [:tr
         [:td (:todo/name row)]
         [:td (:todo/completed-at row)]])]]))
```

### Routes

One thing coast doesn't do yet is update the routes file, let's do that now:

```clojure
(ns routes)

(def routes [[:get "/"          :home.index/view :home]
             [:get "/404"       :error.not-found/view :404]
             [:get "/500"       :error.server-error/view :500]
             [:get "/todo/list" :todo.list/view]])
```

Now we can check it out in the browser, there's no styling or anything so it's not going to look amazing, start up a repl with `make repl` and run `(server/-main)` then go to `http://localhost:1337/todo/list` to check out your handiwork.

Example projects and more coming soon...

## Read The Docs

The docs are still under construction, but there should be enough there
to get a production-ready website off the ground

[Read the docs](docs/README.md)

## Contributing

Any contribution is welcome! Submit a PR and let's get it merged in!

## License

Coast on Clojure is released under the [MIT License](https://opensource.org/licenses/MIT).
