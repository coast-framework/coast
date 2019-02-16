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

Here's how the new migrations work

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
(pull '* [:author/id 1])
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

## Querying

Querying is largely the same, there are new helpers like

```clojure
(coast/fetch :author 1)
```

This retrieves the whole row by primary key (assuming your primary key is id). Other notable differences are the requirement of a `from` statement in all queries:

```clojure
(coast/q '[:select * :from author])
```

Previously you could omit the `from` and do this:

```clojure
(coast/q '[:select author/*])
```

This may come back but I don't believe it works for this version. Another small change to pull queries inside of `q`

```clojure
(coast/q '[:pull author/id
                 {:author/posts [post/title post/body]}
           :from author]
```

Previously you had to surround the pull symbols with a vector, now you don't have to!

Another thing that's changed is `transact` has been deprecated in favor of the much simpler insert/update/delete functions:

```clojure
(coast/insert {:member/handle "sean"
               :member/email "sean@swlkr.com"
               :member/password "whatever"
               :member/photo "/some/path/to/photo.jpg"})

(coast/update {:member/id 1
               :member/email "me@seanwalker.xyz"})

(coast/delete {:member/id 1})
```

You can also pass vectors of maps as well and everything should work assuming all maps have the same columns and all maps in `update` have a primary key column specified

Lesser known but will now work

```clojure
(coast/execute! '[:update author
                  :set email = ?email
                  :where id = ?id]
                {:email "new-email@email.com"
                 :id [1 2 3]})
```

Oh one last thing about insert/update/delete. They no longer return the value that was changed, they just return the number of records changed.

## Exception Handling

There was quite a bit of postgres specific code related to raise/rescue, that is gone now since the postgres library isn't included anymore, which means any postgres exceptions like foreign key constraint violations or unique constraint violations will show up as exceptions in application code.

## Routing

Routing hasn't changed really, old routing code will still work and continue to work, there have only been syntactic improvements. Before the convention was to `def routes` now the convention is to `defn routes []` and return the route vector like so:

```clojure
(ns routes
  (:require [coast]
            [components]))

(defn routes []
  (coast/wrap-with-layout components/layout
    [:get "/" :home/index]))
```

Before you had to wrap all vectors in another vector, that's now optional, it makes things a little cleaner. Also multiple layout support per batch of routes is easier as well since you no longer have to pass layout in `app`.

## Views

Views have changed quite a bit, previous versions of coast treated code files like controllers that return html and that's back again, so before each file was separated in view/action function pairs in folders for each "action" that's not the case any more, the default layout for code is now this:

```clojure
; src/<table>.clj
(defn index [request])
(defn view [request])
(defn build [request])
(defn create [request])
(defn edit [request])
(defn change [request])
(defn delete [request])
```

`index` and `view` correspond to a list/table page and a single row page.

`build` and `create` correspond to a new database row form page and a place to submit that form and insert the new row into the database

`edit` and `change` represent a form to edit an existing row and a place to submit that form and update the row in the db

`delete` represents you guessed it a place to submit a delete form.

There are a few new helpers too, even though the old view helpers will still work:

```clojure
(ns home
  (:require [coast]))

(coast/redirect-to ::index)
```

This is a combination of `redirect` and `url-for` and it makes the handlers so much cleaner.

There's also `form-for`

```clojure
(ns home
  (:require [coast]))

(defn edit [request]
  (coast/form-for ::change {:author/id 1}))
```

This is a combination of `coast/form` and `action-for`.

Those are it for the major changes in coast.theta. Hope you like it!
