# Getting Started

* [Supported Databases](#user-content-supported-databases)
* [Setup](#user-content-setup)
* [Basic Example](#user-content-basic-example)
* [Connection Pooling](#user-content-connection-pooling)
* [Debugging](#user-content-debugging)

Coast was originally conceived as a simple, yet powerful clojure database library.

It evolved and grew from a yesql clone into a full-fledged database management library of it's own including a custom query builder, ORM (minus the O and the M), and custom clojure syntax migrations as well.

In this guide we'll learn to setup and use the *database provider*.

NOTE: Coast uses the [java.jdbc](https://github.com/clojure/java.jdbc) and hikari (https://github.com/brettwooldridge/HikariCP) libraries under the hood

## Supported Databases
The list of supported databases and their drivers:

| database   | driver                                               |
| :------------ | :------------------------------------------------------ |
| postgresql | [pgjdbc](https://github.com/pgjdbc/pgjdbc)           |
| sqlite3    | [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) |

## Setup

### Installation
Coast by default installs the sqlite driver for you in the `deps.edn` file:

```clojure
; deps.edn
{:deps {org.xerial/sqlite-jdbc {:mvn/version "3.25.2"}}}
```

You can install the postgres driver the same way:

```clojure
; deps.edn
{:deps {org.postgresql/postgresql {:mvn/version "42.2.5"}}}
```

### Database Creation

Coast has a two handy cli commands to create and drop databases in production or development:

```bash
make db/create
```

and

```bash
make db/drop
```

This uses the `:coast-env` key from the `env.edn` file (or the `COAST_ENV` environment variable) to determine which database from the `db.edn` file to create. For example given this `env.edn`:

```clojure
{:coast-env "dev"}
```

and this `db.edn`

```clojure
{:dev {:database "project_zero_dev.sqlite3"
       :adapter "sqlite"}}
```

The file `project_zero_dev.sqlite3` will be created in your project directory when you execute `make db/create`.

### Configuration
Coast uses the `sqlite` connection by default.

The default connection can be set via the `db.edn` file:

```clojure
; db.edn
{:dev {:database "project_zero_dev.sqlite3"
       :adapter "sqlite"}

 :test {:database "project_zero_test.sqlite3"
        :adapter "sqlite"}

 :prod {:database #env :db-name
        :adapter "sqlite"}}
```

Postgres can be set similarly:

```clojure
; db.edn
{:dev {:database "project_zero_dev"
       :adapter "postgres"}

 :test {:database "project_zero_test"
        :adapter "postgres"}

 :prod {:database #env :db-name
        :adapter "postgres"
        :username #env :db-username
        :password #env :db-password
        :host #env :db-host
        :port #env :db-port}}
```

## Basic Example
Coast [queries](Queries.md) has a data-driven API, meaning all queries are clojure vectors, similar to [views](Views.md) and the [routes](Routes.md).

NOTE: Coast uses the singular version of all words for table names which is in opposition to most other full stack frameworks.

For example to select all people from the person table:

```clojure
(coast/q '[:select * :from person])
; => [{:person/email "" :person/first-name "" :person/last-name ""}]
```

### Where Clause
To add a where clause to a query, add a `:where` keyword to the vector:

```clojure
(coast/q '[:select *
           :from person
           :where [first-name "sean"]])
```

If you need more power out of your where query, feel free to pass a sql vector instead:

```clojure
(coast/q '[:select *
           :from person
           :where ["age > ?" 18]])
```

To add multiple where clauses ANDed together, add multiple vectors:

```clojure
(coast/q '[:select *
           :from person
           :where [first-name "sean"]
                  ["age > ?" 18]])
```

Multiple where clauses OR'd together? prefix with the `or` symbol:

```clojure
(coast/q '[:select *
           :from person
           :where or [first-name "sean"]
                     ["age > ?" 18]])
```

See the [queries](Queries.md) documentation for the complete API reference.

## Connection Pooling

Coast pools connections for reuse and all used connections are maintained unless the process dies.

To close all connections, call the `.close` method:

```clojure
(.close (:datasource (coast/connection)))
```

## Debugging
Debugging database queries can be handy in both development and production.

Coast has a very simple debugging mechanism, set `:debug` to true in `db.edn`:

```clojure
{:dev {:database "project_zero_dev.sqlite3"
       :adapter "sqlite"
       :debug true}}
```

This enables printing of all sql queries to **stdout**.
