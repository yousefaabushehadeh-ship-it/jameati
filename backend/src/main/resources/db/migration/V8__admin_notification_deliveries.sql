create table admin_notification_deliveries (
  id bigint not null auto_increment,
  created_by bigint not null,
  title varchar(180) not null,
  body varchar(1000) not null,
  link varchar(500),
  audience varchar(20) not null,
  in_app_enabled boolean not null default false,
  email_enabled boolean not null default false,
  status varchar(30) not null,
  scheduled_at datetime(6) not null,
  claimed_at datetime(6),
  started_at datetime(6),
  completed_at datetime(6),
  recipient_count integer not null default 0,
  in_app_sent_count integer not null default 0,
  email_sent_count integer not null default 0,
  failed_count integer not null default 0,
  error_message varchar(1000),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint fk_admin_notification_creator foreign key (created_by) references users (id)
);

create index idx_admin_notification_due
  on admin_notification_deliveries (deleted, status, scheduled_at, claimed_at);

create table admin_notification_recipients (
  id bigint not null auto_increment,
  delivery_id bigint not null,
  user_id bigint not null,
  in_app_status varchar(20) not null,
  email_status varchar(20) not null,
  in_app_error varchar(1000),
  email_error varchar(1000),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_admin_notification_recipient unique (delivery_id, user_id),
  constraint fk_admin_notification_recipient_delivery foreign key (delivery_id) references admin_notification_deliveries (id),
  constraint fk_admin_notification_recipient_user foreign key (user_id) references users (id)
);

create index idx_admin_notification_recipient_status
  on admin_notification_recipients (delivery_id, in_app_status, email_status, deleted);
