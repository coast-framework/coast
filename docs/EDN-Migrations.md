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
coast gen migration add-blog-schema
```

## No more tables

In this new EDN driven migration world there are only relationships, columns, identities and constraints. Here's what it looks like practically using the migration file from earlier

```clojure
; add-blog-schema.edn
[{:db/ident :author/name
  :db/type "citext"}

 {:db/ident :author/email
  :db/type "citext"}

 {:db/rel :author/posts
  :db/type :many
  :db/joins :post/author}

 {:db/ident :post/slug
  :db/type "citext"}

 {:db/col :post/title
  :db/type "text"
  :db/nil? false}

 {:db/col :post/body
  :db/type "text"
  :db/nil? false}

 {:db/col :post/published-at
  :db/type "timestamptz"}]
```

This works the same as the SQL migrations, run `make db/migrate` from your terminal and `make db/rollback` to rollback

## Idents

It's a lot to take in, so we'll start at the top with idents

```clojure
{:db/ident :author/name
 :db/type "citext"}
```

This is an ident, it's a map with two key value pairs, the qualified name of the column (i.e which table to put it under), and the type of the column. Here's the SQL that gets generated from that tiny map

```sql
create table if not exists author (
  id serial primary key,
  updated_at timestamptz,
  created_at timestamptz not null default now()
);

alter table author add column name citext unique not null;
alter table author add column email citext unique not null;
```

That's a lot less typing and a lot of stuff that just happened. "idents" are just unique columns of any type that you'll reference later
in some really cool query stuff. Let's skip the other idents since they're all the same and get to the meat and potatoes
of this thing, the relationships or rels for short because coast is terse.

## Relationships

### Many Rels

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

alter table post add column author integer not null references author (id) on delete cascade
```

So this is a one to many relationship and the foreign key is defined in the `:db/joins` part there. So still some manual work on your part, but you always
get a not null references and a delete cascade. You don't have to remember the syntax for that stuff anymore, which is great!

### One Rels

If you need more control over the column that is referenced from the `:db/joins` substitute it with `:db/ref` instead and then take control of your destiny with a one rel:

```clojure
{:db/rel :author/posts
 :db/type :many
 :db/ref :post/author} ; ref is the same as rel one line down

{:db/rel :post/author
 :db/type :one
 :db/delete "delete"} ; this is the default, you can specify "restrict", "set default", "set null" or "no action" as well
```

## Columns

Regular old columns, no unique indices, no relationships, just data storage

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

That's nice for simple schemas, but what about the more interesting stuff, like join tables and compound unique constraints? DONT PANIC 😱

## Constraints

Coast has support for compound unique constraints!

```clojure
[{:db/constraint [:vote/member :vote/link]
  :db/type "unique"}]
```

## All Together Now

Here's a better example, let's say that you want people to be able to up vote links, but you don't want them to be able to upvote more than once. You could handle this in your application, but why do that when postgres can do it for you? Do less, ship 🚢 faster!

```clojure
[{:db/ident :member/name
  :db/type "citext"} ; alter table member add column name citext unique not null;

 {:db/col :link/name
  :db/type "text"
  :db/nil? false} ; alter table link add column name text not null;

 {:db/col :link/url
  :db/type "citext"
  :db/nil? false} ; alter table link add column url citext not null;

 {:db/rel :member/links
  :db/type :many
  :db/ref :link/member} ; 👇 :db/ref is :link/member

 {:db/rel :link/member  ; 👆 :db/rel is :link/member
  :db/type :one
  :db/delete "no action"} ; alter table link add column member integer references member(id) not null

 {:db/rel :member/votes
  :db/type :many
  :db/ref :vote/member}

 {:db/rel :link/votes
  :db/type :many
  :db/ref :vote/link}

 {:db/rel :vote/link
  :db/type :one
  :db/delete "no action"} ; alter table vote add column link integer references link(id) not null on delete no action;

 {:db/rel :vote/member
  :db/type :one
  :db/delete "no action"} ; alter table vote add column member integer references member(id) not null on delete no action;

 {:db/constraint [:vote/member :vote/link]
  :db/type "unique"}]
```

There's a lot going on and although the many rels don't affect the db, it's nice to have them for the *sophisticated* queries you can do later.

## Reference

Here's a handy dandy reference of all of the keys that you can pass into a migration:

## Idents

```clojure
{:db/ident :table/column
 :db/type "postgresql column type"}
```

## Constraints

```clojure
{:db/constraint [:table/column :table1/column1]
 :db/type "unique"}
```

## Rels

### Many rels

```clojure
{:db/rel :any/name.you.want
 :db/type :many
 :db/ref :table/col}

{:db/rel :table/col
 :db/type :one
 :db/delete "restrict"}
```

```clojure
; or shorthand
{:db/rel :any/name.you.want
 :db/type :many
 :db/joins :table/col}
```

### One rels

```clojure
{:db/rel :table/col
 :db/type :one
 :db/delete "no action"} ; or cascade, restrict, set null, set default
```

## Cols
```clojure
{:db/col :table/column
 :db/type "postgres column type"
 :db/nil? true or false
 :db/default "a default value"}
```
