# Pull Syntax

* [Motivation](#user-content-motivation)
* [Setup](#user-content-setup)
* [Pull](#user-content-pull)
* [Pull Many](#user-content-pull-many)
* [Limit](#user-content-limit)
* [Order By](#user-content-order-by)

## Motivation

Typically in web applications we treat data as if it were tree shaped not table shaped.

For example, when you join two or more tables together you get "duplicate" data that you then want to group.

Here's a more concrete example:

```clojure
(ns your-project
  (:require [coast]))

(coast/q '[:select author/screen-name
                   author/email
                   post/title
                   post/body
           :from author
           :join post
           :where [author/id ?author/id]]
         {:author/id 1})
```

The following query mirrors the SQL almost one to one, it generates this SQL

```sql
select author.screen_name,
       author.email,
       post.title,
       post.body
from author
join post on post.author = author.id
where author.id = ?
```

Which assuming some data, would output this in your Clojure code

```clojure
[{:author/screen-name "cody-coast" :author/email "cody@coastonclojure.com" :post/title "First!" :post/body "Post!"}
 {:author/screen-name "cody-coast" :author/email "cody@coastonclojure.com" :post/title "Second!" :post/body "Post!"}
 {:author/screen-name "cody-coast" :author/email "cody@coastonclojure.com" :post/title "Third!" :post/body "Post!"}]
```

It would be nice if there were just one `:author/screen-name` and one `:author/email` but a vector of posts, like this:

```clojure
[{:author/screen-name "cody-coast"
  :author/email "cody@coastonclojure.com"
  :author/posts [{:post/title "First"
                  :post/body "Post!"}
                 {:post/title "Second!"
                  :post/body "Post!"}
                 {:post/title "Third!"
                  :post/body "Post!"}]}]
```

Pull syntax is shamelessly stolen from [datomic](https://www.datomic.com) and it solves this problem. You don't even need an ORM to pull it off.

## Setup

Pull requires some information on top of your database's schema to function.

This information is defined in the `db/associations.clj` file.

Here's a quick walk through of defining a database migration with two tables and setting up `associations.clj`

```clojure
(defn change []
  (create-table :author
    (text :screen-name :unique true :null false)
    (text :email :unique true :null false)
    (text :password :null false)
    (bool :pro :null false :default false)
    (timestamps)))
```

This first migration defines an `author` table that looks like this:

| column | type |
|--------|------|
| id     | integer primary key |
| screen_name | text |
| email  | text |
| password | text |
| pro      | boolean |
| updated_at | timestamp |
| created_at | timestamp |

The second migration defines a `post` table:

```clojure
(defn change []
  (create-table :post
    (references :author)
    (text :title)
    (text :body)
    (timestamps)))
```

| column | type |
|--------|------|
| id     | integer primary key |
| author   | integer references author(id) |
| title  | text |
| body | text |
| updated_at | timestamp |
| created_at | timestamp |

The last step is to define a relationship between author and post in `db/associations.clj`:

```clojure
; db/associations.clj

(ns associations
  (:require [coast.db.associations :refer [table belongs-to has-many tables]]))

(defn associations []
  (tables
    (table :author
      (has-many :posts))

    (table :post
      (belongs-to :author))))
```

The way associations works is when querying a database with pull syntax, Coast uses the relationship names, so you can query columns with the `qualified keyword` syntax of `table/name`, but you can also query relationships.

For this particular example, you can now query for posts like this:

```clojure
{:author/posts [post/id post/title]}
```

and you can query the other direction like this:

```clojure
{:post/author [author/screen-name]}
```

## Pull

Pull syntax groups child records into vectors under a name you define.

Building on the setup section, here is a complete example of a pull query:

```clojure
(coast/pull '[author/screen-name
              author/email
              author/password
              {:author/posts [post/title
                              post/body]}]
            {:author/id 1})
```

This will return the following clojure map:

```clojure
{:author/screen-name "cody-coast"
 :author/email "cody@coastonclojure.com"
 :author/posts [{:post/title "First"
                 :post/body "Post!"}
                {:post/title "Second!"
                 :post/body "Post!"}
                {:post/title "Third!"
                 :post/body "Post!"}]}
```

The shape of the query almost looks like the shape of the data returned if you squint and tilt your head a bit.

Pull queries are a bit different from regular SQL because the relationships themselves are in the query instead of just the `join` keyword.

Pull queries work similarly to SQL, you can also pull things without fully specifying all of the columns using an asterisk `*`:

```clojure
(coast/pull '[*] {:author/id 1})
```

This will recursively pull every column from every table related to `author`, including the `author` table itself, until it reaches the end of the relationships defined in `db/associations.clj`.

## Pull Many

This works well for one row with a specified primary key column and value, but what about more arbitrary where clauses?

Coast has you covered

```clojure
(coast/q '[:pull author/screen-name
                 {:author/posts [post/title
                                 post/body]}
           :from author
           :where [pro ?pro]]
         {:pro true})
```

NOTE: When pulling multiple rows from the database, a surrounding vector `[]` is optional

The above query might return something like the following:

```clojure
[{:author/screen-name "cody-coast"
  :author/posts [{:post/title "First"
                  :post/body "Post!"}
                 {:post/title "Second!"
                  :post/body "Post!"}
                 {:post/title "Third!"
                  :post/body "Post!"}]}
  {:author/screen-name "sean"
    :author/posts [{:post/title "Coast is the best"
                    :post/body "Word."}]}]

```

Catch-all asterisks `*` work here too:

```clojure
(coast/q '[:pull *
           :from author
           :where [pro ?pro]]
         {:pro true})
```

## Limit

Normally pull will keep returning all of the child rows without limit. There is a way to specify a maximum number of rows.

```clojure
(q '[:pull author/screen-name
           {(:author/posts :limit 10) [post/title
                                       post/body]}
     :from author
     :where [author/pro ?pro]]
   {:pro true})
```

If you wrap the relationship portion of the query in `()` you can pass in `:limit` and a number.

## Order By

It's also possible to order the results returned from a pull query as well:

```clojure
(q '[:pull author/screen-name
           {(:author/posts :order post/id desc) [post/title
                                                 post/body]}
     :where [author/pro ?pro]]
   {:pro true})
```
