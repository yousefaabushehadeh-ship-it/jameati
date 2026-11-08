alter table opportunities add column source_external_key varchar(190);
alter table opportunities add column source_hash varchar(64);
alter table opportunities add column last_imported_at datetime(6);
alter table opportunities add column verified_by varchar(120);

create unique index uk_opportunity_source_external_key
  on opportunities (source_name, source_external_key);

create table import_runs (
  id bigint not null auto_increment,
  source_name varchar(180) not null,
  source_version varchar(80) not null,
  resource_type varchar(40) not null,
  status varchar(30) not null,
  total_rows integer not null default 0,
  created_rows integer not null default 0,
  updated_rows integer not null default 0,
  skipped_rows integer not null default 0,
  failed_rows integer not null default 0,
  completed_at datetime(6),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id)
);

create index idx_import_run_created on import_runs (created_at);

-- The original demo opportunities are soft-deleted once the verified official feed is available.
-- This is deliberately limited to the exact seed signature and remains recoverable.
update opportunities
set published = false, deleted = true
where source_name = 'بيانات جامعتي التجريبية'
  and slug like 'demo-opportunity-%';
