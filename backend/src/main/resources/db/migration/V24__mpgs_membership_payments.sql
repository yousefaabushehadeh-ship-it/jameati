alter table annual_memberships add column payment_status varchar(30) not null default 'PENDING';
alter table annual_memberships add column gateway_order_id varchar(40);
alter table annual_memberships add column gateway_authentication_transaction_id varchar(50);
alter table annual_memberships add column gateway_transaction_id varchar(50);
alter table annual_memberships add column gateway_session_id varchar(64);
alter table annual_memberships add column gateway_result varchar(40);
alter table annual_memberships add column gateway_response_code varchar(80);

create unique index uk_membership_gateway_order on annual_memberships (gateway_order_id);
create unique index uk_membership_gateway_auth_transaction on annual_memberships (gateway_authentication_transaction_id);
create unique index uk_membership_gateway_transaction on annual_memberships (gateway_transaction_id);
create index idx_membership_payment_status on annual_memberships (payment_provider, payment_status, updated_at);
