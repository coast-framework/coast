# Schema

Relational databases have always been kind of a pain to work with from an application development standpoint.
On the one hand, it's nice that we have relations and it saves space or something, on the other, I really just
want to query my database in a way that makes sense to me. I don't want to have to think about joins
and grouping things, just do it and preferably do it declaratively. That's where `coast.schema` comes in.

## The goal

To save you from typing out SQL DDT syntax by hand and hopefully offering you a performant and a higher level
way of thinking of data storage other than slogging through joins and thinking about indices. [Here's a good overview
of a few advantages of letting Coast handle your schema for you.](https://github.com/mozilla/mentat#data-storage-is-hard)

## What is it

There's two sides to every database driven web application. The first side is the database schema. The second side
is about reading and writing from the database. An advantage of a full stack framework is that you can start abstracting
things that only makes sense when you control how schema migrations are done. Let's get more concrete.

## Migrations

Existing SQL migrations in Coast still work, which is great, know SQL? good, you can migrate your database and do whatever you
want with your database schema. So this is an option for people who don't care what the schema looks like necessarily, or people
who want to let the framework take care of that and get some benefits from it. Here's what this new schema migration looks like.

From your terminal in your Coast app folder run this

```bash
coast gen schema add-authors-and-posts
```

So that's a lot of stuff, the naming doesn't really matter, it's just nice to see a summary, here's where an understanding of schema
comes in.

## No more tables

In this new EDN driven migration world there are only relationships, columns and identities. There are no tables. Tables are abstracted
away for you by Coast. Here's what it looks like practically using the migration file we made earlier

```clojure
; add-authors-and-posts
[{:db/ident :author/name
  :db/type "text"}

 {:db/ident :author/email
  :db/type "text"}

 {:db/rel :author/posts
  :db/type :many
  :db/joins :post/author}

 {:db/rel :author/comments
  :db/type :many
  :db/joins :comment/author}

 {:db/ident :post/slug
  :db/type "text"}

 {:db/col :post/title
  :db/type "text"
  :db/nil? false}

 {:db/col :post/body
  :db/type "text"
  :db/nil? false}

 {:db/col :post/published-at
  :db/type "timestamptz"}]
```

## Idents

It's a lot to take in, so we'll start at the top with idents

```clojure
{:db/ident :author/name
 :db/type "text"}
```

Remember how there aren't any tables anymore? There still are, but Coast handles them for you. This is an ident, it's
a map with two keys and values, the qualified name of the column (i.e which table to put it under), and the type of the column.
So, not really as cool as "there are no tables", since you still have to tell Coast which table to put the column under, but it sounds
great, so I said it. Here's the SQL that gets generated from that one map

```sql
create table if not exists author (
  id serial primary key,
  updated_at timestamptz,
  created_at timestamptz not null default now()
);

alter table author add column name text unique not null;
```

That's a lot less typing and a lot of stuff that just happened. "Idents" are just unique columns of any type you want that you'll reference later
in some really cool query stuff. That's all there is to that. Let's skip the other idents since they're all the same and get to the meat and potatoes
of this thing, the relationships (or rels for short cause Coast is cool)

## Relationships

Relationships are just shorthand for joins, but it's less typing and less searching for SQL references syntax

```clojure
{:db/rel :author/posts
 :db/type :many
 :db/joins :post/author}
```

Before we get to the SQL, I want to stop here and talk about what ORMs usually do which is plural table names and then singular class names. In Coast there are
no classes and no objects so there is no translation of plural to singular and the complexity that comes with that. Everything is singular, every table name
and every keyword that references a table/column pair is the same. It's weird to have table names be singular, but that's how it is. One advantage this gives
is that relationships can be named with the plural and it makes more sense and is less ambiguous to the framework, but I digress, here's the SQL

```sql
create table if not exists author (
  id serial primary key,
  updated_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists post (
  id serial primary key,
  updated_at timestamptz,
  created_at timestamptz not null default now()
);

alter table post add column author_id integer not null references author (id) on delete cascade
```

So this is a one to many relationship (currently the only supported relationship 😞) and the foreign key is defined in the `:db/joins` part there. So still some manual work on your part, but you always
get a not null references and a delete cascade. You don't have to remember the syntax for that stuff anymore, which is great!

## Columns

The last piece of the puzzle are regular old columns, no unique indices, no relationships, just straightforward data storage

```clojure
{:db/col :post/title
 :db/type "text"
 :db/nil? false}
```

Here's the SQL for this one
```sql
create table if not exists post (
  id serial primary key,
  updated_at timestamptz,
  created_at timestamptz not null default now()
);

alter table post add column title text not null;
```

One thing that you'll notice is this only works for tables with zero rows, if there are rows, you'll need to specify a default with a `:db/default` key.

## Reference

Here's a handy dandy reference of all of the keys that you can pass into a migration:

## Idents

```clojure
{:db/ident :table/column
 :db/type "postgresql column type"}
```

## Rels

```clojure
{:db/rel :any/name.you.like
 :db/type :many ; currently the only supported type
 :db/joins :belongs-to-table/refernces-table}
```

## Cols
```clojure
{:db/col :table/column
 :db/type "postgres column type"
 :db/nil? true or false
 :db/default "a default value"}
```
