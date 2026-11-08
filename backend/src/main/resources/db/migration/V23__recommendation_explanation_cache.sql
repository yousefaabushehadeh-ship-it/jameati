create table recommendation_explanation_cache (
  id bigint auto_increment primary key,
  user_id bigint not null,
  recommendation_type varchar(30) not null,
  candidate_id bigint not null,
  input_fingerprint varchar(64) not null,
  payload_json text not null,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  deleted boolean not null default false,
  constraint fk_rec_explanation_user foreign key (user_id) references users (id),
  constraint uk_rec_explanation_input unique (
    user_id, recommendation_type, candidate_id, input_fingerprint)
);

create index idx_rec_explanation_lookup on recommendation_explanation_cache (
  user_id, recommendation_type, candidate_id, input_fingerprint, deleted
);
