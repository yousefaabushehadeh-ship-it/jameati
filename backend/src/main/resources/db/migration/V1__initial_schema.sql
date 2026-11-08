create table users (
  id bigint not null auto_increment,
  email varchar(190) not null,
  password_hash varchar(255) not null,
  full_name varchar(120) not null,
  enabled boolean not null default true,
  email_verified_at datetime(6),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_users_email unique (email)
);

create table user_roles (
  user_id bigint not null,
  role varchar(30) not null,
  constraint fk_user_roles_user foreign key (user_id) references users (id)
);

create table refresh_tokens (
  id bigint not null auto_increment,
  token_hash varchar(64) not null,
  user_id bigint not null,
  expires_at datetime(6) not null,
  revoked_at datetime(6),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_refresh_token_hash unique (token_hash),
  constraint fk_refresh_token_user foreign key (user_id) references users (id)
);

create table opportunities (
  id bigint not null auto_increment,
  slug varchar(190) not null,
  title varchar(300) not null,
  short_description varchar(500) not null,
  description text not null,
  category varchar(100) not null,
  organization varchar(180) not null,
  country varchar(100) not null,
  funding varchar(30) not null,
  deadline date,
  deadline_text varchar(120),
  always_open boolean not null default false,
  featured boolean not null default false,
  published boolean not null default false,
  cover_image varchar(500),
  official_url varchar(500),
  apply_url varchar(500),
  source_name varchar(180),
  source_url varchar(500),
  last_verified_at datetime(6),
  views bigint not null default 0,
  apply_clicks bigint not null default 0,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_opportunity_slug unique (slug)
);

create index idx_opportunity_deadline on opportunities (deadline);
create index idx_opportunity_published on opportunities (published, created_at);

create table opportunity_specialities (
  opportunity_id bigint not null,
  speciality varchar(255),
  constraint fk_opportunity_speciality foreign key (opportunity_id) references opportunities (id)
);

create table opportunity_nationalities (
  opportunity_id bigint not null,
  nationality varchar(255),
  constraint fk_opportunity_nationality foreign key (opportunity_id) references opportunities (id)
);

create table opportunity_degree_levels (
  opportunity_id bigint not null,
  degree_level varchar(255),
  constraint fk_opportunity_degree foreign key (opportunity_id) references opportunities (id)
);

create table catalog_items (
  id bigint not null auto_increment,
  type varchar(30) not null,
  slug varchar(190) not null,
  title varchar(300) not null,
  summary varchar(500) not null,
  content text not null,
  category varchar(100) not null,
  organization varchar(180),
  country varchar(100),
  image varchar(500),
  website varchar(500),
  featured boolean not null default false,
  published boolean not null default true,
  views bigint not null default 0,
  rating double,
  meta varchar(80),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_catalog_type_slug unique (type, slug)
);

create index idx_catalog_type_published on catalog_items (type, published);

create table favorites (
  id bigint not null auto_increment,
  user_id bigint not null,
  opportunity_id bigint not null,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_favorite_user_opportunity unique (user_id, opportunity_id),
  constraint fk_favorite_user foreign key (user_id) references users (id),
  constraint fk_favorite_opportunity foreign key (opportunity_id) references opportunities (id)
);
