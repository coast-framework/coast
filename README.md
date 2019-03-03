## What is this?

Coast is a full stack web framework written in Clojure for small teams or solo developers. It uses a relational database and renders html on the server without javascript which allows you to ship your web applications faster

```clojure
(ns my-project
  (:require [coast]))

(def routes
  (coast/routes
    (coast/site-routes
      [:get "/" :home])))

(defn home [req]
  [:h1 "You're coasting on clojure!"])

(def app (coast/app {:routes routes}))

(coast/server app {:port 1337})
```

## Getting Started

Getting started with Coast is detailed below, but if you want to really get into it, [there are some docs too](docs/README.md)

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
├── db
│   └── migrations
│   └── associations.clj
├── deps.edn
├── resources
│   ├── assets.edn
│   └── public
│       ├── css
│       │   └── app.css
│       │   └── tachyons.min.css
│       └── js
│           └── app.js
│           └── jquery.js
├── src
│   ├── components.clj
│   ├── home.clj
│   ├── routes.clj
│   └── server.clj
└── test
    └── server_test.clj
```

### Databases

For the sake of this tutorial, we want to show a list of todos. In coast, that means making a place for these todos to live, in this case (and in every case): start with the database. You can make a database with a handy shortcut that coast gives you:

```bash
make db/create
# clj -A\:db/create
# Database todos_dev.sqlite3 created successfully
```

This will create a sqlite database with the name of your project and whatever `COAST_ENV` is set to, which by default is `dev`. So the database name will be `todos_dev`.

### Migrations

Now that the database is created, let's generate a migration:

```bash
coast gen migration create-table-todo name:text finished-at:timestamp
# db/migrations/20190926190239_create_table_todo.clj created
```

This will create a file in `db/migrations` with a timestamp and whatever name you gave it, in this case: `create_table_todo`

```clojure
(ns migrations.20190926190239-create-table-todo
  (:require [coast.db.migrations :refer :all])
  (:refer-clojure :exclude [boolean]))

(defn change []
  (create-table :todo
    (text :name)
    (timestamp :finished-at)
    (timestamps)))
```

This is clojure, not sql, although plain sql migrations would work just fine. Time to apply this migration to the database:

```bash
make db/migrate
# clj -A\:db/migrate
#
# -- Migrating:  20190926190239-create-table-todo ---------------------
#
#  (create-table :todo
#    (text :name)
#    (timestamp :finished-at)
#    (timestamps)))
#
# -- 20190926190239-create-table-todo ---------------------
#
# 20190926190239-create-table-todo migrated successfully
```

This updates the database schema with a `todo` table. Time to move on to the clojure code.

### Generators

Now that the database has been migrated, this is where coast's generators come in. Rather than you having to type everything out by hand and read docs as you go, generators are a way to get you started and you can customize what you need from there.

This will create a file in the `src` directory with the name of a table. Coast is a pretty humble web framework, there's no FRP or graph query languages or anything. There are just files with seven functions each: `build`, `create`, `view`, `edit`, `change`, `delete` and `index`.

```bash
coast gen code todo
# src/todo.clj created successfully
```

That file looks like this

```clojure
(ns todo
  (:require [coast]))

(defn index [request]
  (let [rows (coast/q '[:select *
                        :from todo
                        :order id
                        :limit 10])]
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

There's more to the file, but you can check it out yourself. Just from this example, coast is using clojure's vectors and a library called `hiccup` to generate html, and it's also using vectors to generate sql with coast's own internal library for parsing/querying the database.

### Routes

One thing coast doesn't do yet is update the routes file, let's do that now:

```clojure
(ns routes)
  (:require [coast]
            [components])

(def routes
  (coast/routes
    (coast/site-routes components/layout
      [:get "/"      :home/index]
      [:get "/todos" :todo/index]

      [:404 :home/not-found]
      [:500 :home/server-error])))
```

The routes are also clojure vectors, with each element of the route indicating which http method, url and function to call, along with an optional route name if you don't like the `namespace`/`function` name.

Let's check it out from the terminal. Run this

```bash
make server
```

or navigate to the `src/server.clj` file and type this:

```clojure
(comment
  (-main))
```

Then put your cursor somewhere inside of `(-main)` and send this over to the running repl server (made with `make repl` from the terminal).

I currently use [proto-repl](https://github.com/jasongilman/proto-repl), check it out if you want a smooth clojure REPL experience.

Finally, navigate to http://localhost:1337/todos and check out your handiwork.

## Contributing

Any contribution is welcome! Submit a PR and let's get it merged in!

## License

Coast on Clojure is released under the [MIT License](https://opensource.org/licenses/MIT).
