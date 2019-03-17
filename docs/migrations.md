# Migrations

* [Creating Migrations](#user-content-creating-migrations)
* [Migration Files](#user-content-migration-files)
* [Column Types](#user-content-column-types)
* [Schema Table API](#user-content-schema-table-api)
* [Run Migrations](#user-content-run-migrations)
* [SQL Migrations](#user-content-sql-migrations)

Database migrations are the key to all data driven web applications. Coast makes them easy.

## Creating Migrations

To make a new migration call this from your shell in the same directory as your Coast app

```bash
coast gen migration create-table-member
```

This generates a file in the `db/migrations` folder with a timestamp and the name `create_table_member.clj`:

```clojure
(ns migrations.20190926190239-create-table-member
  (:require [coast.db.migrations :refer :all]))

(defn change []
  (create-table :member
    (timestamps)))
```

NOTE: The primary key column `id` is automatically included with the `create-table` function.

There is also a way to specify columns from the shell as well:

```bash
coast gen migration create-table-member email:text nick-name:text password:text photo:text
```

Which yields:

```clojure
(ns migrations.20190926190239-create-table-member
  (:require [coast.db.migrations :refer :all]))

(defn change []
  (create-table :member
    (text :email)
    (text :nick-name)
    (text :password)
    (text :photo)
    (timestamps)))
```

## Migration Files

A migration file requires either two functions `up` and `down` or a `change` function. The `change` function attempts to automatically detect the opposite sql it should generate on a rollback function. So, `create-table` becomes `drop-table`. `add-column` becomes `drop-column`. The `up` and `down` functions are still required for the edges.

NOTE: Things less straightforward than creating/dropping tables may not do what you intend in `change` so be prepared to write `up` and `down` migrations still for things like `add-column`/`drop-column`.

#### `up`

The `up` function is run when you call `make db/migrate`

#### `down`

The `down` function is run when you call `make db/rollback`

#### `change`

The `change` function is run when you call both.

NOTE: It's recommended to keep your migrations as simple as possible.

Usually each migration file will either create a table or add a few columns or add indices but not all of those at the same time.

It's easier to find out which migration changed which part of the database when they do separate and specific things.

## Column Types

This is the full list of column types supported by Coast migrations:

### Column Types

| function    | description |
| :------------- | :------------- |
| text        | adds a text column |
| timestamp   | adds a timestamp column |
| datetime    | adds a datetime column |
| timestamptz | adds a timezone specific timestamp column (postgres only) |
| integer     | adds an integer column |
| bool        | adds a boolean column |
| decimal     | adds a decimal column |
| timestamps  | adds "created_at" and "updated_at" columns |
| json        | adds a json column |

### Column Modifiers

This is the full list of column attributes you can pass into each column function

| key         | value      | description |
| :------------- | :------------ | :------------- |
| :collate    | string     | sets the column collation (e.g. `utf8_unicode`) |
| :null       | true/false | adds "not null" if null is set to false |
| :unique     | true/false | creates a unique constraint for this column |
| :default    | any value  | sets the default column value on insert |
| :references | column     | creates a foreign key constraint on this column |
| :on-delete  | cascade, set null, set default, no action | takes the specified action on delete |
| :on-update  | cascade, set null, set default, no action | takes the specified action on update |

TIP: Don't create multiple tables in a single schema file. Instead, create a new file for each database change. This way you keep your database atomic and can roll back to any version.

## Migration Commands

Below is the list of available migration commands.

### Command List
| command | description |
| :--------- | :------------- |
| `coast gen migration ` | creates a new migration file in `db/migrations` |
| `make db/migrate` | runs all pending migration files |
| `make db/rollback` | rolls back the most recent migration file |


## Schema Table API
Below is the list of schema functions available to interact with database tables

#### create-table
Create a new database table:

```clojure
(defn change []
  (create-table :person))
```

Create a new table if it doesn't already exist:

```clojure
(defn change []
  (create-table :person {:if-not-exists true}))
```

Create a new table with a primary key other than `id`:

```clojure
(defn change []
  (create-table :person {:primary-key "person_id"}))
```

#### rename-table
Rename an existing database table:

```clojure
(defn change []
  (rename-table :person :people))
```

#### drop-table
Drop a database table:

```clojure
(defn down []
  (drop-table :person))
```

NOTE: Typically you can rely on the `change` function with `create-table` to drop tables for you on rollback.

#### `(add-column table column-name column-type)`
Alter a table and add a column

```clojure
(defn change []
  (add-column :person :first-name :text :null false :unique true ...))
```

See the list of column modifiers above for the full list of arguments.

#### `(add-foreign-key from to & {col :col pk :pk fk-name :name :as m})`

```clojure
(defn change []
  (add-foreign-key :todo :person))
```

#### `(add-index table-name cols & {:as m})`

```clojure
(defn change []
  (add-index :person :first-name))

; or for multiple columns

(defn change []
  (add-index :person [:first-name :last-name]))

; or for a unique index

(defn change []
  (add-index :person [:first-name :last-name] :unique true))
```

#### `(add-reference table-name ref-name & {:as m})`

```clojure
(defn change []
  (add-reference :todo :person))
```

#### `alter-column`

```clojure
(defn change []
  (alter-column :person :first-name :json))
```

#### `rename-column`

```clojure
(defn change []
  (rename-column :person :first-name :f-name))
```

#### `rename-index`

```clojure
(defn change []
  (rename-index :old-index :new-index))
```

#### `create-extension`

```clojure
(defn change []
  (create-extension "extension name"))
```

#### `drop-extension`

```clojure
(defn down []
  (drop-extension "extension name"))
```

#### `drop-column`

```clojure
(defn down []
  (drop-column :person :first-name))
```

#### `drop-foreign-key`

```clojure
(defn down []
  (drop-foreign-key :todo :table :person))
```

#### `drop-index`

```clojure
(defn down []
  (drop-index :person :column :first-name))

; or multiple columns

(defn down []
  (drop-index :person :column [:first-name :last-name]))

; or by name

(defn down []
  (drop-index :person :name "person_first_name_index"))
```

#### `drop-reference`

```clojure
(defn down []
  (drop-reference :todo :person))
```

## Run Migrations

To run migrations, run the makefile command `db/migrate` from your terminal

#### make db/migrate

```bash
make db/migrate
```

The change function handles both `migrations` and `rollbacks` of a schema.

So `create-table :member` becomes `drop-table :member` when calling `make db/rollback`.

#### make db/rollback

To undo the last migration run `db/rollback`:

```bash
make db/rollback
```

## SQL Migrations

Plain SQL migrations in plain sql files are also supported

Append `.sql` to the end of a migration name to create a SQL migration:

```bash
coast gen migration the-name-of-a-migration.sql
```

This creates a sql migration instead of a clojure one.

Here's an example `sql` migration

```sql
-- up
create table customer (
  id serial primary key,
  email text unique not null,
  password text not null,
  first_name text,
  last_name text
);

-- down
drop table customer;

```
