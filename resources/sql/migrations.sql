-- name: create-table
create table if not exists schema_migrations
  (id text,
   created_at timestamp without time zone default (now() at time zone 'utc'))

-- name: migrations
select
  id
from
  schema_migrations
order by
  created_at

-- name: insert
insert into schema_migrations (id)
values (:id)

-- name: delete
delete
from
  schema_migrations
where id = :id
