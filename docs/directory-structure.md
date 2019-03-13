# Directory Structure

* [Directory Structure](#user-content-directory-structure)

The Coast directory structure may feel overwhelming at first glance since there are a handful of pre-configured directories.

Eventually, you'll understand what is there because of clojure, java or coast itself, but in the mean time it's a decent way to structure web apps with tools.deps

A standard Coast installation looks something like so:
```bash
.
├── Makefile
├── README.md
├── bin
│   └── repl.clj
├── db
│   └── migrations/
│   └── associations.clj
├── db.edn
├── deps.edn
├── env.edn
├── resources
│   ├── assets.edn
│   └── public
│       ├── css
│       │   └── app.css
│       └── js
│           └── app.js
├── src
│   ├── components.clj
│   ├── home.clj
│   ├── routes.clj
│   └── server.clj
└── test
    └── server_test.clj
```

## Root Directories

### bin

The `bin` directory is the home of the [nREPL](https://github.com/nrepl/nrepl) server.

### db

The `db` directory is used to store all database related files.

### resources

The `resources` directory is used to serve static assets over HTTP.

This directory is mapped to the root of your website:

```clojure
; actual file is stored in /resources/css/app.css
[:link {:rel "stylesheet" :href "/app.css"}]
```

### src

The `src` directory is used to store `components` (re-usable bits of html), and your application code, one file for each set of routes.

### test

The `test` directory is used to store all your application tests.
