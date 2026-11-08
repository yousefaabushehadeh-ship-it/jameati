create table crm_student_leads (
  id bigint not null auto_increment,
  version bigint not null default 0,
  user_id bigint,
  owner_id bigint,
  full_name varchar(160) not null,
  phone varchar(40) not null,
  email varchar(190) not null,
  date_of_birth date,
  city varchar(100),
  education_level varchar(100),
  major varchar(180),
  english_level varchar(60),
  main_need varchar(120),
  interest varchar(180),
  source varchar(80) not null,
  status varchar(40) not null,
  last_contact_at datetime(6),
  next_follow_up_at datetime(6),
  next_action varchar(500),
  service_package varchar(180),
  amount_jod decimal(12,2),
  payment_status varchar(30) not null,
  summary_notes varchar(2000),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_crm_student_lead_user unique (user_id),
  constraint fk_crm_student_lead_user foreign key (user_id) references users (id),
  constraint fk_crm_student_lead_owner foreign key (owner_id) references users (id)
);

create index idx_crm_lead_status_owner on crm_student_leads (deleted, status, owner_id, updated_at);
create index idx_crm_lead_follow_up on crm_student_leads (deleted, next_follow_up_at);
create index idx_crm_lead_email on crm_student_leads (email);
create index idx_crm_lead_phone on crm_student_leads (phone);

create table crm_b2b_accounts (
  id bigint not null auto_increment,
  version bigint not null default 0,
  owner_id bigint,
  organization varchar(200) not null,
  organization_type varchar(30) not null,
  contact_person varchar(160) not null,
  job_title varchar(160),
  phone varchar(40) not null,
  email varchar(190),
  city varchar(100),
  student_count integer,
  source varchar(100) not null,
  stage varchar(30) not null,
  last_contact_at datetime(6),
  next_follow_up_at datetime(6),
  next_action varchar(500),
  offer varchar(500),
  estimated_value_jod decimal(12,2),
  proposal_sent_at datetime(6),
  probability integer not null default 0,
  summary_notes varchar(2000),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint fk_crm_b2b_owner foreign key (owner_id) references users (id),
  constraint ck_crm_b2b_probability check (probability between 0 and 100)
);

create index idx_crm_b2b_stage_owner on crm_b2b_accounts (deleted, stage, owner_id, updated_at);
create index idx_crm_b2b_follow_up on crm_b2b_accounts (deleted, next_follow_up_at);

create table crm_follow_ups (
  id bigint not null auto_increment,
  version bigint not null default 0,
  student_lead_id bigint,
  b2b_account_id bigint,
  owner_id bigint,
  completed_by bigint,
  due_at datetime(6) not null,
  action varchar(500) not null,
  status varchar(30) not null,
  result varchar(2000),
  completed_at datetime(6),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint fk_crm_follow_up_lead foreign key (student_lead_id) references crm_student_leads (id),
  constraint fk_crm_follow_up_b2b foreign key (b2b_account_id) references crm_b2b_accounts (id),
  constraint fk_crm_follow_up_owner foreign key (owner_id) references users (id),
  constraint fk_crm_follow_up_completed_by foreign key (completed_by) references users (id),
  constraint ck_crm_follow_up_target check (
    (student_lead_id is not null and b2b_account_id is null)
    or (student_lead_id is null and b2b_account_id is not null)
  )
);

create index idx_crm_follow_up_queue on crm_follow_ups (deleted, status, due_at, owner_id);
create index idx_crm_follow_up_lead on crm_follow_ups (student_lead_id, status, due_at);
create index idx_crm_follow_up_b2b on crm_follow_ups (b2b_account_id, status, due_at);

create table crm_activities (
  id bigint not null auto_increment,
  student_lead_id bigint,
  b2b_account_id bigint,
  actor_id bigint,
  type varchar(40) not null,
  details varchar(3000) not null,
  occurred_at datetime(6) not null,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint fk_crm_activity_lead foreign key (student_lead_id) references crm_student_leads (id),
  constraint fk_crm_activity_b2b foreign key (b2b_account_id) references crm_b2b_accounts (id),
  constraint fk_crm_activity_actor foreign key (actor_id) references users (id),
  constraint ck_crm_activity_target check (
    (student_lead_id is not null and b2b_account_id is null)
    or (student_lead_id is null and b2b_account_id is not null)
  )
);

create index idx_crm_activity_lead on crm_activities (student_lead_id, occurred_at);
create index idx_crm_activity_b2b on crm_activities (b2b_account_id, occurred_at);
