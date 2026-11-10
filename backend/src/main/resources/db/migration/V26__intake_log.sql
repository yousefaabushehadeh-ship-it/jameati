-- سجل كل منشور بيوصل من Tab (واتساب): إيمتى وصل وشو صار فيه
create table intake_log (
  id bigint auto_increment primary key,
  title varchar(300),
  kind varchar(20),
  status varchar(20) not null,
  reason varchar(255),
  item_id bigint,
  item_slug varchar(190),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false
);

create index idx_intake_log_created on intake_log (created_at);
