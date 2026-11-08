alter table opportunities add column version bigint not null default 0;
alter table catalog_items add column version bigint not null default 0;
alter table catalog_items add column attributes_json text;

create table site_settings (
  id bigint not null auto_increment,
  setting_key varchar(190) not null,
  setting_group varchar(50) not null,
  value_json text not null,
  public_visible boolean not null default false,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_site_setting_key unique (setting_key)
);

create index idx_site_settings_group_public on site_settings (setting_group, public_visible, deleted);

create table media_assets (
  id bigint not null auto_increment,
  original_name varchar(255) not null,
  stored_name varchar(255) not null,
  content_type varchar(120) not null,
  size_bytes bigint not null,
  storage_path varchar(500) not null,
  public_url varchar(500) not null,
  uploaded_by varchar(190) not null,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_media_asset_stored_name unique (stored_name)
);

create index idx_media_asset_created on media_assets (deleted, created_at);

create table analytics_events (
  id bigint not null auto_increment,
  event_type varchar(60) not null,
  path varchar(500),
  entity_type varchar(50),
  entity_id varchar(100),
  visitor_hash varchar(64) not null,
  metadata_json text,
  occurred_at datetime(6) not null,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id)
);

create index idx_analytics_event_type_time on analytics_events (event_type, occurred_at);
create index idx_analytics_event_path_time on analytics_events (path, occurred_at);

create table audit_logs (
  id bigint not null auto_increment,
  actor_email varchar(190),
  actor_roles varchar(500),
  action varchar(80) not null,
  resource_type varchar(80) not null,
  resource_id varchar(190),
  detail text,
  correlation_id varchar(100),
  ip_address varchar(64),
  success boolean not null,
  occurred_at datetime(6) not null,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id)
);

create index idx_audit_actor_time on audit_logs (actor_email, occurred_at);
create index idx_audit_resource_time on audit_logs (resource_type, occurred_at);

insert into site_settings
  (setting_key, setting_group, value_json, public_visible, created_at, updated_at, deleted)
values
  ('homepage', 'homepage', '{"hero":{"title":"جامعتي","subtitle":"فرصك تبدأ من هنا"}}', true, current_timestamp(6), current_timestamp(6), false),
  ('seo', 'seo', '{"title":"جامعتي","description":"منصة عربية للفرص التعليمية والمهنية"}', true, current_timestamp(6), current_timestamp(6), false),
  ('settings', 'settings', '{"contactEmail":""}', true, current_timestamp(6), current_timestamp(6), false);
