alter table annual_memberships add column gateway_authentication_status varchar(80);
alter table annual_memberships add column gateway_authentication_version varchar(20);
alter table annual_memberships add column gateway_authentication_stage varchar(30);
alter table annual_memberships add column gateway_authentication_html text;
