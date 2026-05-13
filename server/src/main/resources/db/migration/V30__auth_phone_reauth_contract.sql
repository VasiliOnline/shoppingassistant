alter table auth_users
    add column if not exists pending_phone varchar(64);

alter table auth_users
    add column if not exists pending_phone_requested_at bigint;
