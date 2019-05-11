## What is this?

Coast is a full stack web framework written in Clojure for small teams or solo developers. It uses a relational database and renders html on the server without javascript which allows you to ship your web applications faster

```clojure
(ns server
  (:require [coast]))

(def routes [[:get "/" ::home]])

(defn home [request]
  "You're coasting on clojure!")

(def app (coast/app {:routes routes}))

(coast/server app {:port 1337})
```

## The Docs

[More comprehensive docs are available here](docs/readme.md)

## Quickstart

If you don't want to read the docs, and just want to jump in, you're in the right place.

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

This will take you from a fresh coast installation to a working todo list app.

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
│   ├── db.edn
│   └── associations.clj
├── deps.edn
├── env.edn
├── resources
│   ├── assets.edn
│   └── public
│       ├── css
│       │   ├── app.css
│       │   └── tachyons.min.css
│       └── js
│           ├── app.js
│           └── jquery.min.js
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

This will create a sqlite database by default with the name of the database defined in `db.edn` and the `COAST_ENV` or `:coast-env` environment variable defined in `env.edn`.

### Migrations

Now that the database is created, let's generate a migration:

```bash
coast gen migration create-table-todo name:text finished:bool
# db/migrations/20190926190239_create_table_todo.clj created
```

This will create a file in `db/migrations` with a timestamp and whatever name you gave it, in this case: `create_table_todo`

```clojure
(ns migrations.20190926190239-create-table-todo
  (:require [coast.db.migrations :refer :all]))

(defn change []
  (create-table :todo
    (text :name)
    (bool :finished)
    (timestamps)))
```

This is clojure, not sql, although plain sql migrations would work just fine. Time to apply this migration to the database:

```bash
make db/migrate
# clj -m coast.migrations migrate
#
# -- Migrating:  20190310121319_create_table_todo ---------------------
#
# create table todo ( id integer primary key, name text, finished boolean, updated_at timestamp, created_at timestamp not null default current_timestamp )
#
# -- 20190310121319_create_table_todo ---------------------
#
# 20190310121319_create_table_todo migrated successfully
```

This updates the database schema with a `todo` table. Time to move on to the clojure code.

### Generators

Now that the database has been migrated, this is where coast's generators come in. Rather than you having to type everything out by hand and read docs as you go, generators are a way to get you started and you can customize what you need from there.

This will create a file in the `src` directory with the name of a table. Coast is a pretty humble web framework, there's no FRP or ~~graph query languages~~ or anything. There are just files with seven functions each: `build`, `create`, `view`, `edit`, `change`, `delete` and `index`.

```bash
coast gen code todo
# src/todo.clj created successfully
```

Coast uses a library under the hood called [hiccup](https://github.com/weavejester/hiccup) to generate html.

### Routes

One thing coast doesn't do yet is update the routes file, let's do that now:

```clojure
(ns routes
  (:require [coast]))

(def routes
  (coast/site
    (coast/with-layout :components/layout
      [:get "/" :home/index]
      [:resource :todo] ; add this line)))
```

The routes are also clojure vectors, with each element of the route indicating which http method, url and function to call, along with an optional route name if you don't like the `namespace`/`function` name.

### Start the server

#### From the command line

Let's check it out from the terminal. Run this

```bash
make server
```

and visit `http://localhost:1337/todos` to see the app in action.

#### From the REPL
I currently use [proto-repl](https://github.com/jasongilman/proto-repl), check it out if you want a smooth clojure REPL experience.

First run, the nrepl server:

```bash
make repl
```

Then in your editor, connect to the nrepl server, in atom with proto-repl for example:

Press, `Ctrl+Cmd+Y` and hit `Enter`.

After you're connected, load the `server.clj` file with `Option+Cmd+Shift+F`.

Finally, move your cursor to `(-main)` and evaluate the top block with `Shift+Cmd+B`.

### Check out the page

Navigate to http://localhost:1337/todos and check out your handiwork.

### Tested on Different Platforms 

#### Tested on Clojure 1.10.0 on OSX El Capitan using brew to install Clojure

readline versions might clash depending on your setup. You might need to downgrade to a lower version of readline depending on your version of clojure. For example... readline version 7.0 for clojure 1.9

## Contributing

Any contribution is welcome! Submit a PR and let's get it merged in!

## License

Coast on Clojure is released under the [MIT License](https://opensource.org/licenses/MIT).
