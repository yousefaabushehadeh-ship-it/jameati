-- Seeded role-test accounts. All five accounts use the password: Jameati@Test2026!
-- The password hash also acts as a seed signature before privileged roles are attached.
insert into users
  (email, password_hash, full_name, enabled, email_verified_at, created_at, updated_at, deleted)
select
  'test.user@jameati.test',
  '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC',
  'مستخدم تجريبي', true, current_timestamp(6), current_timestamp(6), current_timestamp(6), false
where not exists (
  select 1 from users where lower(email) = lower('test.user@jameati.test')
);

insert into users
  (email, password_hash, full_name, enabled, email_verified_at, created_at, updated_at, deleted)
select
  'test.data-entry@jameati.test',
  '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC',
  'مدخل بيانات تجريبي', true, current_timestamp(6), current_timestamp(6), current_timestamp(6), false
where not exists (
  select 1 from users where lower(email) = lower('test.data-entry@jameati.test')
);

insert into users
  (email, password_hash, full_name, enabled, email_verified_at, created_at, updated_at, deleted)
select
  'test.editor@jameati.test',
  '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC',
  'محرر تجريبي', true, current_timestamp(6), current_timestamp(6), current_timestamp(6), false
where not exists (
  select 1 from users where lower(email) = lower('test.editor@jameati.test')
);

insert into users
  (email, password_hash, full_name, enabled, email_verified_at, created_at, updated_at, deleted)
select
  'test.admin@jameati.test',
  '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC',
  'مدير تجريبي', true, current_timestamp(6), current_timestamp(6), current_timestamp(6), false
where not exists (
  select 1 from users where lower(email) = lower('test.admin@jameati.test')
);

insert into users
  (email, password_hash, full_name, enabled, email_verified_at, created_at, updated_at, deleted)
select
  'test.super-admin@jameati.test',
  '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC',
  'مدير أعلى تجريبي', true, current_timestamp(6), current_timestamp(6), current_timestamp(6), false
where not exists (
  select 1 from users where lower(email) = lower('test.super-admin@jameati.test')
);

insert into user_roles (user_id, role)
select user_account.id, 'USER'
from users user_account
where lower(user_account.email) = lower('test.user@jameati.test')
  and user_account.password_hash = '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC'
  and not exists (
    select 1 from user_roles assigned_role
    where assigned_role.user_id = user_account.id and assigned_role.role = 'USER'
  );

insert into user_roles (user_id, role)
select user_account.id, 'USER'
from users user_account
where lower(user_account.email) = lower('test.data-entry@jameati.test')
  and user_account.password_hash = '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC'
  and not exists (
    select 1 from user_roles assigned_role
    where assigned_role.user_id = user_account.id and assigned_role.role = 'USER'
  );
insert into user_roles (user_id, role)
select user_account.id, 'DATA_ENTRY'
from users user_account
where lower(user_account.email) = lower('test.data-entry@jameati.test')
  and user_account.password_hash = '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC'
  and not exists (
    select 1 from user_roles assigned_role
    where assigned_role.user_id = user_account.id and assigned_role.role = 'DATA_ENTRY'
  );

insert into user_roles (user_id, role)
select user_account.id, 'USER'
from users user_account
where lower(user_account.email) = lower('test.editor@jameati.test')
  and user_account.password_hash = '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC'
  and not exists (
    select 1 from user_roles assigned_role
    where assigned_role.user_id = user_account.id and assigned_role.role = 'USER'
  );
insert into user_roles (user_id, role)
select user_account.id, 'EDITOR'
from users user_account
where lower(user_account.email) = lower('test.editor@jameati.test')
  and user_account.password_hash = '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC'
  and not exists (
    select 1 from user_roles assigned_role
    where assigned_role.user_id = user_account.id and assigned_role.role = 'EDITOR'
  );

insert into user_roles (user_id, role)
select user_account.id, 'USER'
from users user_account
where lower(user_account.email) = lower('test.admin@jameati.test')
  and user_account.password_hash = '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC'
  and not exists (
    select 1 from user_roles assigned_role
    where assigned_role.user_id = user_account.id and assigned_role.role = 'USER'
  );
insert into user_roles (user_id, role)
select user_account.id, 'ADMIN'
from users user_account
where lower(user_account.email) = lower('test.admin@jameati.test')
  and user_account.password_hash = '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC'
  and not exists (
    select 1 from user_roles assigned_role
    where assigned_role.user_id = user_account.id and assigned_role.role = 'ADMIN'
  );

insert into user_roles (user_id, role)
select user_account.id, 'USER'
from users user_account
where lower(user_account.email) = lower('test.super-admin@jameati.test')
  and user_account.password_hash = '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC'
  and not exists (
    select 1 from user_roles assigned_role
    where assigned_role.user_id = user_account.id and assigned_role.role = 'USER'
  );
insert into user_roles (user_id, role)
select user_account.id, 'ADMIN'
from users user_account
where lower(user_account.email) = lower('test.super-admin@jameati.test')
  and user_account.password_hash = '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC'
  and not exists (
    select 1 from user_roles assigned_role
    where assigned_role.user_id = user_account.id and assigned_role.role = 'ADMIN'
  );
insert into user_roles (user_id, role)
select user_account.id, 'SUPER_ADMIN'
from users user_account
where lower(user_account.email) = lower('test.super-admin@jameati.test')
  and user_account.password_hash = '$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC'
  and not exists (
    select 1 from user_roles assigned_role
    where assigned_role.user_id = user_account.id and assigned_role.role = 'SUPER_ADMIN'
  );
