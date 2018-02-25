-- name: list
select *
from __table
order by created_at
limit = :limit
offset = :offset


-- name: find
-- fn: first
select *
from __table
where id = :id


-- name: insert
-- fn: first
insert into __table (
  __insert-columns
)
values (
  __insert-values
)
returning *


-- name: update
-- fn: first
update __table
set
  __update-columns
where id = :id
returning *


-- name: delete
-- fn: first
delete from __table
where id = :id
returning *
