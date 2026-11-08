alter table annual_memberships add column payment_sender_name varchar(160);
alter table annual_memberships add column rejection_reason varchar(1000);
alter table annual_memberships add column reviewed_by varchar(190);
alter table annual_memberships add column reviewed_at datetime(6);

update site_settings
set value_json = concat(
  substring(value_json, 1, char_length(value_json) - 1),
  ',"cliqAlias":""}'
)
where setting_key = 'subscription'
  and value_json not like '%"cliqAlias"%';
