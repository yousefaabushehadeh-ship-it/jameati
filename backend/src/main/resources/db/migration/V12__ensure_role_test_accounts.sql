-- Recovery migration for deployments where V11 was recorded without the seeded accounts.
-- All five accounts use the password: Jameati@Test2026!
insert ignore into users
  (email, password_hash, full_name, enabled, email_verified_at, created_at, updated_at, deleted)
values
  ('test.user@jameati.test', '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC', 'مستخدم تجريبي', true, current_timestamp(6), current_timestamp(6), current_timestamp(6), false),
  ('test.data-entry@jameati.test', '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC', 'مدخل بيانات تجريبي', true, current_timestamp(6), current_timestamp(6), current_timestamp(6), false),
  ('test.editor@jameati.test', '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC', 'محرر تجريبي', true, current_timestamp(6), current_timestamp(6), current_timestamp(6), false),
  ('test.admin@jameati.test', '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC', 'مدير تجريبي', true, current_timestamp(6), current_timestamp(6), current_timestamp(6), false),
  ('test.super-admin@jameati.test', '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC', 'مدير أعلى تجريبي', true, current_timestamp(6), current_timestamp(6), current_timestamp(6), false);

insert into user_roles (user_id, role)
select seeded_user.id, roles_to_seed.role_name
from users seeded_user
join (
  select 'test.user@jameati.test' as email, 'مستخدم تجريبي' as full_name, 'USER' as role_name
  union all select 'test.data-entry@jameati.test', 'مدخل بيانات تجريبي', 'USER'
  union all select 'test.data-entry@jameati.test', 'مدخل بيانات تجريبي', 'DATA_ENTRY'
  union all select 'test.editor@jameati.test', 'محرر تجريبي', 'USER'
  union all select 'test.editor@jameati.test', 'محرر تجريبي', 'EDITOR'
  union all select 'test.admin@jameati.test', 'مدير تجريبي', 'USER'
  union all select 'test.admin@jameati.test', 'مدير تجريبي', 'ADMIN'
  union all select 'test.super-admin@jameati.test', 'مدير أعلى تجريبي', 'USER'
  union all select 'test.super-admin@jameati.test', 'مدير أعلى تجريبي', 'ADMIN'
  union all select 'test.super-admin@jameati.test', 'مدير أعلى تجريبي', 'SUPER_ADMIN'
) roles_to_seed
  on lower(seeded_user.email) = lower(roles_to_seed.email)
  and seeded_user.full_name = roles_to_seed.full_name
where seeded_user.password_hash = '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC'
  and not exists (
    select 1
    from user_roles assigned_role
    where assigned_role.user_id = seeded_user.id
      and assigned_role.role = roles_to_seed.role_name
  );
