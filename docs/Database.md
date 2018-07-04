# Database

The only currently supported database is postgres. PRs gladly accepted to add more.

There are a few generators to help you get the boilerplate-y database stuff out of the way:

```bash
make db/create
make db/drop
make db/migrate
make db/rollback
coast gen migration
coast gen schema
```

Those all do what you think they do. Except that last one, we'll get to that later.

## `make db/create`

The database name is your project name underscore dev. So
if your project name is `cryptokitties`, your db would be named `cryptokitties_dev`. This assumes a running
postgres server and the process running leiningen has permission to create databases. I don't know what happens
when those requirements aren't met. Probably an error of some kind. You can actually change this from `project.clj`
since the aliases have the name in them as the first argument.

## `make db/drop`

The opposite of `db/create`. Again, I don't know what happens when you run drop before create. Probably an error.

## `coast gen migration`

This creates a migration which is just plain sql 😎 with a timestamp and the filename in the `resources/migrations` folder

## `coast gen migration the-name-of-a-migration`

This creates an empty migration that looks like this

```sql
-- up

-- down

```

## `coast gen migration create-posts`

This creates a migration that creates a new table with the "default coast"
columns of id and created_at.

```sql
-- up
create table posts (
  id serial primary key,
  created_at timestamptz default now()
)

-- down
drop table posts
```

## `coast gen migration create-posts title:text body:text`

This makes a new migration that creates a table with the given name:type
columns.

```sql
-- up
create table posts (
  id serial primary key,
  title text,
  body text,
  created_at timestamptz default now()
)

-- down
drop table posts
```

## `make db/migrate`

This performs the migration
