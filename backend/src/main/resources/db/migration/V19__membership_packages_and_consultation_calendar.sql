alter table annual_memberships add column plan_code varchar(20) not null default 'ANNUAL';
alter table annual_memberships add column billing_period_months integer not null default 12;
alter table annual_memberships add column consultation_progress varchar(30) not null default 'WAITING';
alter table annual_memberships add column consultation_notes varchar(4000);
create index idx_membership_plan on annual_memberships (user_id, plan_code, status, deleted);

create table consultation_schedule (
  id bigint not null primary key,
  enabled boolean not null default true,
  all_day boolean not null default true,
  timezone varchar(60) not null,
  working_days varchar(20) not null,
  start_time varchar(5) not null,
  end_time varchar(5) not null
);
insert into consultation_schedule (id, enabled, all_day, timezone, working_days, start_time, end_time)
values (1, true, true, 'Asia/Amman', '1,2,3,4,5,6,7', '00:00', '23:59');

create table consultation_appointments (
  id bigint not null auto_increment primary key,
  membership_id bigint not null,
  starts_at datetime(6) not null,
  ends_at datetime(6) not null,
  duration_minutes integer not null,
  status varchar(20) not null default 'BOOKED',
  notes varchar(2000),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  constraint fk_consultation_membership foreign key (membership_id) references annual_memberships (id),
  constraint chk_consultation_duration check (duration_minutes in (30, 60)),
  constraint chk_consultation_time check (ends_at > starts_at)
);
create index idx_consultation_overlap on consultation_appointments (status, starts_at, ends_at);
