## What is this?

Coast is a full stack web framework written in Clojure for small teams or solo developers. It uses a relational database and renders html on the server without javascript which allows you to ship your web applications faster

```clojure
(ns server
  (:require [coast]))

(defn home [request]
  (coast/render :text
    "You're coasting on clojure!"))

(def routes (coast/routes [:get "/" home]))

(def app (coast/app routes))

(coast/server app {:port 1337})
```

## Discussion

Feel free to ask questions and join discussion on the [coast gitter channel](https://gitter.im/coast-framework/community).

## Quickstart

1. [Make sure clojure is installed first](https://www.clojure.org/guides/getting_started)

2. Install the coast cli script

```sh
curl -o /usr/local/bin/coast https://raw.githubusercontent.com/coast-framework/coast/next/coast && chmod a+x /usr/local/bin/coast
```

3. Finally, create a new coast project named `todos` and cd into it

```sh
coast new todos && cd todos
```

4. Start the server to make sure it's working

```sh
make server
```

5. Visit `http://localhost:1337` in your browser and marvel at your handiwork.

Not much ado about a todo app with no todos, let's fix that

### Create a database

We're going to make the world's worst todo list app. Be excited.

Start by creating a new database

```sh
coast db create
# clj -m coast.db create
# Database todos_dev.sqlite3 created successfully
```

This will create a sqlite database with the name of the database defined in `db.edn` and the `COAST_ENV` or `:coast-env` environment variable defined in `env.edn`.

### Generate a migration

Next, generate a migration:

```sh
coast gen migration create-table-todo name:text finished:bool
# db/migrations/20190926190239_create_table_todo.clj created
```

This will create a file in `db/migrations` with a timestamp and whatever name you gave it, in this case: `create_table_todo`

```clojure
(ns migrations.20190926190239-create-table-todo
  (:require [db.migrator.helper :refer :all]))

(defn change []
  (create-table :todo
    (text :name)
    (bool :finished)
    (timestamps)))
```

This is clojure, not sql, although plain sql migrations would work just fine.

### Run the migration

Don't forget to run this migration:

```sh
coast db migrate
# clj -m coast.db migrate
#
# -- Migrating:  20190310121319_create_table_todo ---------------------
#
# create table todo ( id integer primary key, name text, finished boolean, updated_at timestamp, created_at timestamp not null default current_timestamp )
#
# -- 20190310121319_create_table_todo ---------------------
#
# 20190310121319_create_table_todo migrated successfully
```

This updates the database schema with a `todo` table.

### Generate a route

Now that the database has been migrated, this is where coast's generators come in. Rather than you having to type everything out by hand and read docs as you go, generators are a way to get you started and you can customize what you need from there.

This will create a file in the `src` directory with the name of a table. Coast is a pretty humble web framework, there's no FRP or graph query languages or anything. There are just files with seven functions each: `build`, `create`, `show`, `edit`, `patch`, `delete` and `index`.

```sh
coast gen route todo
# src/routes/todo.clj created successfully
```

### Update routes file

One thing coast doesn't do yet is update the routes file, let's do that now:

```clojure
(ns routes
  (:require [coast]
            [routes.home :as home]
            [routes.todo :as todo]))


(def routes
  (coast/routes
    home/routes
    todo/routes))
```

The routes are clojure vectors, with each element of the route indicating which http method, url and function to call, along with an optional route name if you don't like the `routes.table`/`function` name.

### Restart the server

Stop the running server if it was still running and start it up again:

```sh
make server
```

Visit `http://localhost:1337/todo` to see the app in action.

## The Docs

[More comprehensive docs are available here](docs/readme.md)

## Contributing

Any contribution is welcome! Submit a PR and let's get it merged in!

## License

Coast on Clojure is released under the [MIT License](https://opensource.org/licenses/MIT).
