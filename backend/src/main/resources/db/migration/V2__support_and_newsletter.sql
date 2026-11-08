create table contact_messages (
  id bigint not null auto_increment,
  reference_code varchar(24) not null,
  name varchar(80) not null,
  email varchar(190) not null,
  subject varchar(30) not null,
  page_url varchar(500),
  message text not null,
  consent boolean not null,
  status varchar(30) not null default 'NEW',
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_contact_message_reference unique (reference_code)
);

create index idx_contact_message_status_created on contact_messages (status, created_at);
create index idx_contact_message_email on contact_messages (email);

create table newsletter_subscribers (
  id bigint not null auto_increment,
  email varchar(190) not null,
  frequency varchar(30) not null,
  subscribed_at datetime(6) not null,
  unsubscribed_at datetime(6),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  primary key (id),
  constraint uk_newsletter_subscriber_email unique (email)
);

create index idx_newsletter_subscriber_active on newsletter_subscribers (deleted, created_at);

create table newsletter_subscriber_topics (
  subscriber_id bigint not null,
  topic varchar(40) not null,
  primary key (subscriber_id, topic),
  constraint fk_newsletter_topic_subscriber
    foreign key (subscriber_id) references newsletter_subscribers (id)
);
