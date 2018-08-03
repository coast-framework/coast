# SQL Migrations

There's a pretty sweet generator to help speed up your database schema

```bash
coast gen migration
```

## `coast gen migration`

This creates a sql file with a timestamp and the filename in the `resources/migrations` folder

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

## `make db/rollback`

This rolls it back
