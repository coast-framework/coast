# Database

The only currently supported database is postgres. PRs gladly accepted to add more.

There are a few generators to help you get the boilerplate-y database stuff out of the way:

```bash
make db/create
make db/drop
make db/migrate
make db/rollback
```

Those all do what you think they do.

## `make db/create`

The database name is your project name underscore dev. So
if your project name is `cryptokitties`, your db would be named `cryptokitties_dev`. This assumes a running
postgres server and the process running leiningen has permission to create databases. I don't know what happens
when those requirements aren't met. Probably an error of some kind. You can actually change this from `project.clj`
since the aliases have the name in them as the first argument.

## `make db/drop`

The opposite of `db/create`. Again, I don't know what happens when you run drop before create. Probably an error.

## `make db/migrate`

This runs all of the pending migrations on your database and it works across both sql migrations and edn migrations in order, so you can create an extension like `create extension citext` for example and then use `citext` as a `:db/ident` in the next migration, pretty cool eh?

## `make db/rollback`

Rolls back one migration, the edn and sql migrations live side by side and they get migrated/rolled back *in order*.
