-- name: columns
select
  table_name, column_name
from
  information_schema.columns
where
  table_schema = 'public'
  and
  table_name = :table_name
order by
  table_name, ordinal_position
