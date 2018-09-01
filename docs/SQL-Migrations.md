# SQL Migrations

## `coast gen sql:migration the-name-of-a-migration`

This creates an empty migration in resources/migrations that looks like this

```sql
-- up

-- down

```

## `make db/migrate`

This performs the migration

## `make db/rollback`

This rolls it back
