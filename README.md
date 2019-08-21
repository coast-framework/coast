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

## 🚨 New version alert 🚨

Check out the [next branch](https://github.com/coast-framework/coast/tree/next) for the changes coming to coast v1.0

## Discussion

Feel free to ask questions on the [coast gitter channel](https://gitter.im/coast-framework/community).

## Docs

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

### Start the dev server

Type this in your terminal in your project directory

```bash
make server
```

and visit `http://localhost:1337` to see the welcoming coast on clojure default page ✨

This should also create a sqlite database with the name of the database defined in `db.edn` and the `COAST_ENV` or `:coast-env` environment variable defined in `env.edn`.

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
      [:resource :todo]))) ; add this line
```

The routes are also clojure vectors, with each element of the route indicating which http method, url and function to call, along with an optional route name if you don't like the `namespace`/`function` name.  

```[:resource :todo]``` sets up basic [CRUD routes](https://coastonclojure.com/docs/routing.md#user-content-route-resources) in one line.

### Start the server

#### From the command line

Let's check it out from the terminal. Run this

```bash
make server
```

and visit `http://localhost:1337/todos` to see the app in action.

#### From the REPL
I currently use atom with [parinfer](https://shaunlebron.github.io/parinfer/) and [chlorine](https://github.com/mauricioszabo/atom-chlorine) as my REPL client, check it out if you want a smooth clojure experience.

First run, the repl socket server:

```bash
make repl
```

Then in your editor, connect to the repl server.

In atom with chlorine for example:

Press `space + c`, fill in the port with `5555` and hit `Enter`.

After you're connected, load the `server.clj` file with `Chlorine: Load File`.

Finally, move your cursor to `(-main)` and evaluate with `Cmd+Enter`.

Navigate to http://localhost:1337/todos and check out your handiwork.

### Tested on Different Platforms

#### Tested on Clojure 1.10.0 on MacOS using brew to install Clojure

readline versions might clash depending on your setup. You might need to downgrade to a lower version of readline depending on your version of clojure. For example... readline version 7.0 for clojure 1.9

## Contributing

Any contribution is welcome! Submit a PR and let's get it merged in!

## License

Coast on Clojure is released under the [MIT License](https://opensource.org/licenses/MIT).
