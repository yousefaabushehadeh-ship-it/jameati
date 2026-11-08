alter table annual_memberships add column applicant_name varchar(160);
alter table annual_memberships add column applicant_email varchar(190);
alter table annual_memberships add column receipt_original_name varchar(255);
alter table annual_memberships add column receipt_stored_name varchar(255);
alter table annual_memberships add column receipt_content_type varchar(40);
alter table annual_memberships add column receipt_size_bytes bigint;
alter table annual_memberships add column receipt_sha256 varchar(64);
alter table annual_memberships add column receipt_uploaded_at datetime(6);
alter table annual_memberships add column auto_activated boolean not null default false;
alter table annual_memberships add column activation_source varchar(40);

create unique index uk_annual_membership_receipt_stored_name
  on annual_memberships (receipt_stored_name);
create unique index uk_annual_membership_receipt_sha256
  on annual_memberships (receipt_sha256);
