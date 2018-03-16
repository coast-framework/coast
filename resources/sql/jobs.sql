-- name: queued
select *
from jobs
where
  finished_at is null
  and (
    scheduled_at <= now()
    or
    scheduled_at is null
  )

-- name: insert
-- fn: first
insert into jobs (
  function,
  args,
  finished_at,
  scheduled_at
)
values (
  :function,
  :args,
  :finished_at,
  :scheduled_at
)
returning *


-- name: update
-- fn: first
update jobs
set
  function = :function,
  args = :args,
  finished_at = :finished_at,
  scheduled_at = :scheduled_at
where id = :id
returning *
