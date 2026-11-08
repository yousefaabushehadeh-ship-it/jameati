alter table opportunities add column service_enabled boolean not null default false;
alter table opportunities add column service_price decimal(10,2);
alter table opportunities add column service_currency varchar(10) not null default 'USD';

create table scholarship_service_subscriptions (
  id bigint not null auto_increment,
  version bigint not null default 0,
  user_id bigint not null,
  opportunity_id bigint not null,
  amount decimal(10,2) not null,
  currency varchar(10) not null,
  payment_status varchar(30) not null,
  application_status varchar(40) not null,
  payment_provider varchar(40) not null default 'MOCK',
  payment_reference varchar(100),
  paid_at datetime(6),
  submitted_at datetime(6),
  reviewed_at datetime(6),
  contacted_at datetime(6),
  full_name varchar(160),
  phone varchar(40),
  whatsapp varchar(40),
  nationality varchar(100),
  residence_country varchar(100),
  date_of_birth date,
  degree_level varchar(100),
  university varchar(180),
  speciality varchar(180),
  graduation_year varchar(20),
  gpa varchar(40),
  english_level varchar(80),
  notes text,
  documents_text text,
  review_notes text,
  reviewed_by varchar(190),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_scholarship_subscription unique (user_id, opportunity_id),
  constraint fk_scholarship_subscription_user foreign key (user_id) references users (id),
  constraint fk_scholarship_subscription_opportunity foreign key (opportunity_id) references opportunities (id)
);

create index idx_scholarship_subscription_student
  on scholarship_service_subscriptions (user_id, deleted, updated_at);
create index idx_scholarship_subscription_review
  on scholarship_service_subscriptions (application_status, payment_status, deleted, updated_at);
