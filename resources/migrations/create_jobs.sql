-- up
create table jobs (
  id serial primary key,
  function text,
  args text,
  finished_at timestamp without time zone,
  scheduled_at timestamp without time zone,
  created_at timestamp without time zone default (now() at time zone 'utc')
)

-- down
drop table jobs
