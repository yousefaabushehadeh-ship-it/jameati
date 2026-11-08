alter table user_profiles add column date_of_birth date;
alter table user_profiles add column institution varchar(180);
alter table user_profiles add column english_level varchar(30);
alter table user_profiles add column experience_summary varchar(3000);
alter table user_profiles add column remote_preferred boolean;

create table user_profile_skills (
  profile_id bigint not null,
  skill varchar(120) not null,
  primary key (profile_id, skill),
  constraint fk_user_profile_skill foreign key (profile_id) references user_profiles (id)
);

create table user_profile_interests (
  profile_id bigint not null,
  interest varchar(120) not null,
  primary key (profile_id, interest),
  constraint fk_user_profile_interest foreign key (profile_id) references user_profiles (id)
);
