# Queries

## What is it

The main advantage of putting your schema in the framework's hands is that one, you don't need to declare things twice, like writing out a rails schema and then having to say "belongs_to" or "has_many" in a model somewhere. Two, you can get away with really short query building vs SQL, which, I love-hate SQL, but I love making web apps faster more than SQL.

## The goal

Remove the O and the M in ORM. Data in, data out. Everything transparent and  declarative, hopefully.

## CRUD in Coast

Not sure if it's any better than CRUD anywhere else, but the R in there is definitely unlike anything in any other web framework. Here's how it works.

## R in CRUD

```clojure
(ns r-in-crud
  (:require [coast.query :as q]))

(q/query
  (q/select :author/name :author/email :post/title :post/body)
  (q/joins :author/posts)
  (q/where :author/name "Johnny"))
```

The following query looks pretty basic and it is, it uses this SQL to query the database

```sql
select author.name, author.email, post.title, post.body
from author
join post on post.author_id = author.id
where author.name = 'Johnny'
```

Which assuming some data, would output this in your Clojure code

```clojure
[{:author/name "Johnny" :author/email "johnny@appleseed.com" :post/title "First!" :post/body "Post!"}
 {:author/name "Johnny" :author/email "johnny@appleseed.com" :post/title "Second!" :post/body "Post!"}
 {:author/name "Johnny" :author/email "johnny@appleseed.com" :post/title "Third!" :post/body "Post!"}]
```

This isn't bad, but you can imagine a few more joins and a few more columns and things might get out of hand.
Even if they didn't get out of hand, you want something like this anyway

```clojure
[{:author/name "Johnny"
  :author/email "johnny@appleseed.com"
  :author/posts [{:post/title "First"
                  :post/body "Post!"}
                 {:post/title "Second!"
                  :post/body "Post!"}
                 {:post/title "Third!"
                  :post/body "Post!"}]}]
```

Well you're in luck, thanks to letting Coast handle your schema, you can do just that. It's called `pull` and yes
it's shamelessly stolen from datomic. This is how it looks.

```clojure
(q/query
 (q/pull [:author/email
          :author/name
          {:author/posts [:post/title :post/body]}])
 (q/where :author/name "Johnny"))
```

Which will output what you saw earlier. It uses the relationship names and data from the schema earlier to build the select and join parts of the query. Applying `limit`, `order by` and `where` parts to the relationships is under construction.

## Insert

Given the schema from the [Schema](../Schema.md) section

```clojure
(ns insert-example
  (:require [coast.db :as db]))

(db/insert {:author/name "Johnny"
            :author/email "johnny@appleseed.com"})
```

## Update

```clojure
(ns update-example
  (:require [coast.db :as db]))

(db/update {:author/email "new@email.com"} [:author/name "Johnny"])
```

## Delete

```clojure
(ns update-example
  (:require [coast.db :as db]))

(db/delete [:author/name "Johnny"])
```

## Upsert

```clojure
(ns update-example
  (:require [coast.db :as db]))

(db/upsert {:author/email "new@email.com"} [:author/name "Johnny"])
```
