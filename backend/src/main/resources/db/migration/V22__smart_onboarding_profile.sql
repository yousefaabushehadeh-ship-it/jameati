alter table user_profiles add column current_status varchar(40);
alter table user_profiles add column current_major varchar(180);
alter table user_profiles add column desired_degree varchar(100);
alter table user_profiles add column academic_average decimal(5,2);
alter table user_profiles add column graduation_year integer;
alter table user_profiles add column years_of_experience integer;
alter table user_profiles add column preferred_work_mode varchar(30);
alter table user_profiles add column preferred_employment_type varchar(30);
alter table user_profiles add column minimum_budget decimal(12,2);
alter table user_profiles add column maximum_budget decimal(12,2);
alter table user_profiles add column willing_to_relocate boolean;
alter table user_profiles add column preferred_study_mode varchar(30);
alter table user_profiles add column target_year integer;
alter table user_profiles add column onboarding_completed boolean not null default true;
alter table user_profiles add column onboarding_completed_at datetime(6);
alter table user_profiles add column profile_completion_percentage integer not null default 0;
alter table user_profiles add column recommendation_profile_version bigint not null default 0;

insert into user_profiles (
  user_id, preferred_language, email_notifications, deadline_reminders, profile_public,
  onboarding_completed, profile_completion_percentage, recommendation_profile_version,
  created_at, updated_at, deleted
)
select u.id, 'ar', true, true, false, true, 0, 0, current_timestamp(6), current_timestamp(6), false
from users u
where u.deleted = false
  and not exists (select 1 from user_profiles p where p.user_id = u.id);

alter table user_profiles alter column onboarding_completed set default false;

create index idx_user_profile_onboarding on user_profiles (user_id, onboarding_completed, deleted);

create table user_profile_goals (
  profile_id bigint not null,
  goal varchar(40) not null,
  primary key (profile_id, goal),
  constraint fk_user_profile_goal foreign key (profile_id) references user_profiles (id)
);

create table user_profile_languages (
  profile_id bigint not null,
  language varchar(80) not null,
  primary key (profile_id, language),
  constraint fk_user_profile_language foreign key (profile_id) references user_profiles (id)
);

create table user_profile_job_titles (
  profile_id bigint not null,
  job_title varchar(160) not null,
  primary key (profile_id, job_title),
  constraint fk_user_profile_job_title foreign key (profile_id) references user_profiles (id)
);
