# SQL Migrations

## `coast gen sql:migration the-name-of-a-migration`

This creates a sql file with a timestamp and the filename in the `resources/migrations` folder. Here's an example migration

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

## `make db/migrate`

This performs the migration

## `make db/rollback`

This rolls it back
