-- up
create table jobs (
  id serial primary key,
  function text,
  args text,
  finished_at timestamp with time zone,
  scheduled_at timestamp with time zone,
  created_at timestamp with time zone default now()
)

-- down
drop table jobs
