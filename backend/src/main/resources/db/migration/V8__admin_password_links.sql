-- An account with no password_hash was invited and its owner has not chosen a password yet.
alter table admins
    alter column password_hash drop not null,
    add column password_token_hash varchar(64),
    add column password_token_expires_at timestamptz;

create unique index uq_admins_password_token_hash on admins (password_token_hash);
