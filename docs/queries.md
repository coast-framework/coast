# Queries

* [Introduction](#user-content-introduction)
* [Schema Conventions](#user-content-schema-conventions)
* [Basic Example](#user-content-basic-example)
* [Selects](#user-content-selects)
* [Where Clauses](#user-content-where-clauses)
* [Joins](#user-content-joins)
* [Ordering and Limits](#user-content-ordering-and-limits)
* [Inserts](#user-content-inserts)
* [Updates](#user-content-updates)
* [Deletes](#user-content-deletes)
* [Helpers](#user-content-helpers)
* [SQL Queries](#user-content-sql-queries)

## Introduction

Coast queries are quite a bit nicer than working with raw sql, there are a few shortcuts you can take
that you can't with yesql style sql files.

This guide is an exhaustive showcase of all of the querying abilities that you too can have when you make your website with Coast.

### Syntax Abstraction

Coast attempts to abstract away database specific syntax so you can write queries in clojure vectors and theoretically switch databases with little to no effort. Think sqlite in development and postgresql in production.

Although, it's taboo to say, but you can easily run a low to medium traffic website (99% of all websites) with sqlite in production.

### Conditional Queries
You can also build incremental queries quite a bit easier instead of mashing SQL strings together.

```clojure
(let [sql "select * from person"
      sql (if condition?
            (str sql " where email = ?")
            sql)])
```

...versus

```clojure
(let [query '[:select * :from person]
      query (if condition?
              (conj query '[:where [email ?email]])
              query)])
```

## Schema Conventions

Coast uses a few conventions when dealing with databases.

### Convention 1

The first thing you'll notice is that every response back from the database uses qualified keywords like this:

```clojure
{:table/column "value"}
```

Given this table named `person`

| column      | type      |
| :------------- | :----------- |
| id          | integer   |
| screen_name | text      |
| email       | text      |
| password    | text      |
| updated_at  | timestamp |
| created_at  | timestamp |

The following query:

```clojure
(coast/q '[:select * :from person])
```

... would return something like this

```clojure
[{:person/id 1 :person/screen-name "sean" :person/email "sean@example.com" :person/password "hashed"}
 {:person/id 2 :person/screen-name "sean1" :person/email "sean1@example.com" :person/password "hashed"}]
```

### Convention 2

The second thing you'll notice is that column names are automatically converted between kebab-case to camel_case and back again in the response.

So `screen_name` in the database becomes `:screen-name` in your code.

### Convention 3

The third thing is that each table on creation uses "id" as it's primary key. This makes generating joins easier.

See [Migrations](Migrations.md) for more details.

## Basic Example
Below is a basic example of a query

```clojure
(coast/q '[:select *
           :from person
           :where [screen-name ?screen-name]
           :limit 1]
         {:screen-name "@sean"})
```

## Selects
You can either select all of the columns in a given table with `*`, use `ident`s or `qualified-ident`s.

```clojure
'[:select id screen-name
  :from person]

; => [{:person/id 123 :person/screen-name "@sean"}]
```

NOTE: All responses from the database return qualified keywords in the format of `table/column`

You can also qualify the columns like this:

```clojure
'[:select person/id person/screen-name
  :from person]
```

## Where Clauses
There are a few options for building up where clauses

#### `:where`

```clojure
'[:select *
  :from person
  :where [id ?id]]
```

The way clojure symbols work, you don't have to put `?id` and pass in the params separately.

This also works:

```clojure
[:select :*
 :from :person
 :where [:id 1]]
```

Notice that every element of every vector is a keyword, not a mix of symbols and keywords.

You can also pass in various operators to the where clause:

```clojure
'[:select *
  :from person
  :where [age > 21]]
```

#### where operators

All of the following work as well:

`>`, `!=`, `<=`, `=>`, `<`, `like`

Coast queries attempt to match up the value with the correct sql operator:

```clojure
(coast/q '[:select *
           :from person
           :where [id ?id]]
         {:id nil})

; => select * from person where id is null
```

```clojure
(coast/q '[:select *
           :from person
           :where [id != ?id]]
         {:id nil})

; => select * from person where id is not null
```

```clojure
(coast/q '[:select *
           :from person
           :where [id like ?screen-name]]
         {:screen-name "%ean"})

; => "select * from person where screen_name like ?", '%ean'
```

You can also pass in vectors to the where clause and it will automatically output an "in" statement

```clojure
(coast/q '[:select *
           :from person
           :where [id ?ids]]
         {:ids [1 2 3]})

; => "select * from person where id in (?, ?, ?)", 1, 2, 3
```

If all else fails, you can pass a sql vector to the where clause as well:

```clojure
(coast/q '[:select *
           :from person
           :where ["id not in (?, ?, ?)" 1 2 3]])

; => "select * from person where id not in (?, ?, ?)", 1, 2, 3
```

This can be used to write subqueries, exists, between, or anything else your SQL loving heart desires.

## Joins

#### joins

```clojure
[:select *
 :from person
 :join todo]

; => "select * from person join todo on todo.person = person.id"
```

This is made easy by using coast's database conventions where every primary key is named "id" and every foreign key column is named after the table it references.

`:left-join`, `:right-join`, `:left-outer-join`, `:right-outer-join`, `:outer-join`, `:full-outer-join`, `:full-join` and `:cross-join` all work similarly.

You can construct the join yourself as well:

```clojure
[:select *
 :from person
 :join [todo person/id todo/person-id]]
```

Feel free to pass strings to `:join` as well:

```clojure
[:select *
 :from person
 :join "todo on todo.person_id = person.id"
       "tag on tag.todo = todo.id"]
```

## Ordering and Limits

#### distinct
```clojure
'[:select :distinct age pet
  :from person]

; => select distinct age, pet from person
```

#### group-by
```clojure
'[:select age
  :from person
  :group-by age]

; or

'[:select age
  :from person
  :group age]
```

#### order
```clojure
'[:select *
  :from person
  :order age desc name asc]
```

#### having
```clojure
'[:select age
  :from person
  :group age
  :having age > 21]
```

#### offset/limit
```clojure
'[:select *
  :from person
  :offset 11
  :limit 10]
```

## Inserts

#### insert

```clojure
(coast/insert {:person/email "test@example.com" :person/screen-name "test"})
```

You can also insert multiple records at once

```clojure
(coast/insert [{:person/email "test1@test.com" :person/screen-name "test1"}
               {:person/email "test2@test.com" :person/screen-name "test2"}])
```

Feel free to not use the helper and just use execute! instead (which is similar to `q`)

```clojure
(coast/execute! [:insert person/email person/screen-name
                 :values [["test1@test.com" "test1"]
                          ["test2@test.com" "test2"]]])

; => (2)
```

NOTE: `execute!` returns a list of the number of rows inserted, to get the actual number try `first` on the result

## Updates

```clojure
(coast/update {:person/id 1 :person/last-name "Appleseed" :person/first-name "Johnny"})
```

`update` requires an `:id` key

It can also take a list of maps

```clojure
(coast/update [{:person/id 1 :person/last-name "Appleseed"}
               {:person/id 2 :person/last-name "Newton"}])
```

`execute!` works here too

```clojure
(coast/execute! [:update person
                 :set [person/first-name "Isaac"]
                      [person/last-name "Newt"]
                 :where [person/last-name "Newton"]])
```

## Deletes

#### delete

Delete only deletes rows by primary key `:id`

```clojure
(coast/delete {:person/id 1})
```

`execute!` works here too!

```clojure
(coast/execute! [:delete
                 :from person
                 :where [person/last-name "Newton"]])
```

## Helpers

#### pluck
`pluck` takes a query and returns the first result, which is kind of weird, but that's what it's called

```clojure
(coast/pluck [:select * :from person :where [id 1]])
```

#### fetch
`fetch` returns a given row by primary key

```clojure
(coast/fetch :person 1) ; => {:person/first-name "Johnny" :person/last-name "Appleseed"}
```

#### cols
Returns the columns for a given table

```clojure
(coast/cols :person)
```

## SQL Queries
In Coast there are two ways to pass in plain old sql queries

### `defq`
`defq` works by creating a `.sql` file in `resources/sql` and then calling that files
from clojure with `defq` and instantly having access to all of that files sql bits.

Here's some SQL in a sql file: `resources/sql/posts.sql`

```sql
-- name: find-by-id
-- fn: first!
select *
from posts
where posts.id = :id
limit 1

-- name: all
select *
from posts

-- name: insert
insert into posts (
  title,
  body
) values (
  :title,
  :body
)

-- name: update
update posts
set title = :title,
    body = :body
where posts.id = :id

-- name: delete
delete from posts where id = :id
```

Here's a clojure file named `posts.clj` inside of the `db` folder with the namespace `db.posts`:

```clojure
(ns db.posts
  (:require [coast]))

(coast/defq "sql/posts.sql")
```

This generates functions `find-by-id`, `insert`, `update` and `delete` in the `db.posts` namespace at compile time.

Which means now this will work:

```clojure
(db.posts/insert {:title "title" :body "body"})
```

and this:

```clojure
(db.posts/find-by-id {:id 1}) ; => {:id 1 :title "title" :body "body"}
```

Each generated function takes a single map and returns a list of maps from the database.

NOTE: The maps and the returned rows as maps, do NOT have qualified keywords.

### `q`

`q` also takes a sql vector with plain old sql like so:

```clojure
(coast/q ["select * from person where id = ?" 1])
```

This will return:

```clojure
[{:first-name "Johnny" :last-name "Appleseed" :id 1}]
```

Again, not namespace qualified.
