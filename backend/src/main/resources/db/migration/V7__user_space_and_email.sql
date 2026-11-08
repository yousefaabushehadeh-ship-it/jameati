create table user_profiles (
  id bigint not null auto_increment,
  user_id bigint not null,
  headline varchar(160),
  bio varchar(1200),
  phone varchar(40),
  avatar_url varchar(500),
  country varchar(100),
  city varchar(100),
  nationality varchar(100),
  degree_level varchar(100),
  primary_speciality varchar(180),
  preferred_language varchar(12) not null default 'ar',
  preferred_funding varchar(30),
  email_notifications boolean not null default true,
  deadline_reminders boolean not null default true,
  profile_public boolean not null default false,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_user_profile_user unique (user_id),
  constraint fk_user_profile_user foreign key (user_id) references users (id)
);

create table user_profile_categories (
  profile_id bigint not null,
  category varchar(100) not null,
  primary key (profile_id, category),
  constraint fk_user_profile_category foreign key (profile_id) references user_profiles (id)
);

create table user_profile_countries (
  profile_id bigint not null,
  country varchar(100) not null,
  primary key (profile_id, country),
  constraint fk_user_profile_country foreign key (profile_id) references user_profiles (id)
);

create table user_profile_specialities (
  profile_id bigint not null,
  speciality varchar(180) not null,
  primary key (profile_id, speciality),
  constraint fk_user_profile_speciality foreign key (profile_id) references user_profiles (id)
);

create table user_applications (
  id bigint not null auto_increment,
  user_id bigint not null,
  opportunity_id bigint not null,
  status varchar(30) not null,
  notes text,
  applied_at datetime(6),
  next_action_at datetime(6),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_user_application unique (user_id, opportunity_id),
  constraint fk_user_application_user foreign key (user_id) references users (id),
  constraint fk_user_application_opportunity foreign key (opportunity_id) references opportunities (id)
);

create index idx_user_application_status on user_applications (user_id, status, updated_at);

create table user_notifications (
  id bigint not null auto_increment,
  user_id bigint not null,
  type varchar(40) not null,
  title varchar(180) not null,
  body varchar(1000) not null,
  link varchar(500),
  read_at datetime(6),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint fk_user_notification_user foreign key (user_id) references users (id)
);

create index idx_user_notification_inbox on user_notifications (user_id, deleted, read_at, created_at);

create table followed_organizations (
  id bigint not null auto_increment,
  user_id bigint not null,
  organization_id bigint,
  organization varchar(180) not null,
  country varchar(100),
  website varchar(500),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_followed_organization unique (user_id, organization),
  constraint uk_followed_organization_item unique (user_id, organization_id),
  constraint fk_followed_organization_user foreign key (user_id) references users (id),
  constraint fk_followed_organization_item foreign key (organization_id) references catalog_items (id)
);

create table recently_viewed_opportunities (
  id bigint not null auto_increment,
  user_id bigint not null,
  opportunity_id bigint not null,
  viewed_at datetime(6) not null,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_recently_viewed_opportunity unique (user_id, opportunity_id),
  constraint fk_recently_viewed_user foreign key (user_id) references users (id),
  constraint fk_recently_viewed_opportunity foreign key (opportunity_id) references opportunities (id)
);

create index idx_recently_viewed_user on recently_viewed_opportunities (user_id, viewed_at);

create table cv_documents (
  id bigint not null auto_increment,
  user_id bigint not null,
  title varchar(180) not null,
  language varchar(12) not null,
  template_key varchar(60) not null,
  content_json json not null,
  primary_document boolean not null default false,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint fk_cv_document_user foreign key (user_id) references users (id)
);

create index idx_cv_document_user on cv_documents (user_id, deleted, updated_at);

create table personality_results (
  id bigint not null auto_increment,
  user_id bigint not null,
  personality_type varchar(20) not null,
  scores_json json not null,
  answers_json json,
  completed_at datetime(6) not null,
  latest_result boolean not null default true,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint fk_personality_result_user foreign key (user_id) references users (id)
);

create index idx_personality_result_latest on personality_results (user_id, latest_result, completed_at);

create table email_tokens (
  id bigint not null auto_increment,
  user_id bigint not null,
  token_hash varchar(64) not null,
  token_type varchar(30) not null,
  expires_at datetime(6) not null,
  used_at datetime(6),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_email_token_hash unique (token_hash),
  constraint fk_email_token_user foreign key (user_id) references users (id)
);

create index idx_email_token_active on email_tokens (user_id, token_type, used_at, expires_at);

create table newsletter_campaigns (
  id bigint not null auto_increment,
  created_by bigint not null,
  subject varchar(200) not null,
  preview_text varchar(300),
  html_content text not null,
  topic varchar(40),
  status varchar(30) not null,
  scheduled_at datetime(6),
  started_at datetime(6),
  completed_at datetime(6),
  recipient_count integer not null default 0,
  sent_count integer not null default 0,
  failed_count integer not null default 0,
  error_message varchar(1000),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint fk_newsletter_campaign_creator foreign key (created_by) references users (id)
);

create index idx_newsletter_campaign_status on newsletter_campaigns (status, scheduled_at, created_at);
