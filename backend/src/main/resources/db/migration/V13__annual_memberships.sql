create table annual_memberships (
  id bigint not null auto_increment,
  version bigint not null default 0,
  user_id bigint not null,
  amount decimal(10,2) not null,
  currency varchar(3) not null,
  status varchar(20) not null,
  payment_provider varchar(40) not null default 'MOCK',
  payment_reference varchar(100),
  paid_at datetime(6),
  starts_at datetime(6),
  expires_at datetime(6),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint fk_annual_membership_user foreign key (user_id) references users (id)
);

create index idx_annual_membership_user_history
  on annual_memberships (user_id, deleted, created_at);
create index idx_annual_membership_active
  on annual_memberships (user_id, status, starts_at, expires_at, deleted);
create index idx_annual_membership_admin
  on annual_memberships (status, deleted, updated_at);

insert ignore into site_settings
  (setting_key, setting_group, value_json, public_visible, created_at, updated_at, deleted)
values
  ('subscription', 'subscription',
   '{"enabled":false,"annualPrice":0.00,"currency":"USD","billingPeriodMonths":12,"freeLimit":4,"freeContent":{"OPPORTUNITY":[],"COURSE":[],"JOB":[],"UNIVERSITY":[],"ORGANIZATION":[],"ARTICLE":[],"SPECIALITY":[]}}',
   true, current_timestamp(6), current_timestamp(6), false);
