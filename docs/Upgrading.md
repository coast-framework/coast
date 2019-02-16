# Upgrading from eta

* [Getting Started](#user-content-getting-started)
* [Migrations](#user-content-migrations)
* [Schema](#user-content-schema)
* [Querying](#user-content-querying)
* [Exception Handling](#user-content-exception-handling)
* [Routing](#user-content-routing)
* [Views](#user-content-views)

The **theta** release contains a number of bug fixes and API improvements to keep the code base simple. Breaking changes were kept to a minimum, however, they could not be eliminated entirely.

## Getting Started

The first step to upgrade from `eta` to `theta` is to update coast itself and add your database driver to `deps.edn`

```clojure
; deps.edn
{:deps {coast-framework/coast.theta {:mvn/version "1.0.0"}
        org.postgresql/postgresql {:mvn/version "42.2.5"}
       ; or for sqlite
       org.xerial/sqlite-jdbc {:mvn/version "3.25.2"}}}
```

This is the first release where multiple databses (postgres and sqlite) are supported, but it also means that the database driver is up to you, not coast, similar to all of the other web frameworks out there.

The next step is to add another path to `deps.edn`'s `:paths` key:

```clojure
; deps.edn
{:paths ["db" "src" "resources"]}
```

The db folder is now where all database related files are stored instead of resources

Finally, re-download the `coast` shell script just like if you were installing coast again for the first time. There is a reason it's `coast.theta` and not `coast.eta`

```bash
curl -o /usr/local/bin/coast https://raw.githubusercontent.com/coast-framework/coast/master/coast && chmod a+x /usr/local/bin/coast
```

## Migrations

There were a *just a few* changes to the way database migrations and database schema definitions are handled, so instead of confusing edn migrations which should still be supported, you can now define migrations with clojure and define the schema yourself as well. Plain SQL migrations still work and will always work.

Here's how the new clj migrations work

```bash
coast gen migration create-table-member email:text nick-name:text password:text photo:text
```

This generates a file in the db folder that looks like this:

```clojure
(ns migrations.20190926190239-create-table-member
  (:require [coast.db.migrations :refer :all])
  (:refer-clojure :exclude [boolean]))

(defn change []
  (create-table :member
    (text :email)
    (text :nick-name)
    (text :password)
    (text :photo)
    (timestamps)))
```

There are also a lot more helpers for database-agnostic columns and references, which are detailed in [Migrations](Migrations.md)

Previously, this was a confusing mess of edn without any clear rhyme or reason. Hopefully this is an improvement over that. Running migrations is the same as before:

```bash
make db/migrate
```

This does not generate a `resources/schema.edn` like before because the schema for relationships has been separated out and is now defined by you, which means pull queries not only work with `*` as in

```clojure
(pull :* [:author/id 1])
; or
(q '[:pull *
     :from author]) ; this will recursively pull the whole database starting from the author table
```

but this also means that pull queries and the rest of coast works with existing database schemas. Here's how

## Schema

Before, the schema was tied to the database migrations, which seems like a great idea in theory, but in practice it made the migrations complex and brittle. Coast has moved away from that and has copied rails style schema definitions like so:

```clojure
; db/associations.clj
(ns associations
  (:require [coast.db.associations :refer [table belongs-to has-many tables]]))

(defn associations []
  (tables
    (table :member
      (has-many :todos))

    (table :todo
      (belongs-to :member))))
```

This new associations file is essentially rails' model definitions all rolled into the same file because in coast you don't need models, just data in -> data out. These functions also build what was `schema.edn` but you have a lot more control over the column names, the table names and foreign key names, so something like this would also work

```clojure
; db/associations.clj
(ns associations
  (:require [coast.db.associations :refer [table belongs-to has-many tables]]))

(defn associations []
  (tables
    (table :users
      (primary-key "uid")
      (has-many :todos :table-name "items"
                       :foreign-key "item_id"))

    (table :todos
      (primary-key "uid")
      (belongs-to :users :foreign-key "uid"))))
```

There's also support for "shortcutting" through intermediate join tables which gives the same experience as a "many to many" relationship:

```clojure
; db/associations.clj
(ns associations
  (:require [coast.db.associations :refer [table belongs-to has-many tables]]))

(defn associations []
  (tables
    (table :member
      (has-many :todos))

    (table :todo
      (belongs-to :member)
      (has-many :tagged)
      (has-many :tags :through :tagged))

    (table :tagged
      (belongs-to :todo)
      (belongs-to :tag)

    (table :tag
      (has-many :tagged)
      (has-many :todos :through :tagged)))))
```
