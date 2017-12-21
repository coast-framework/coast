-- name: all
select *
from {{table}}
order by created_at desc

-- name: find-by-id
-- fn: first
select *
from {{table}}
where id = :id

-- name: where
where id = :id
returning *
