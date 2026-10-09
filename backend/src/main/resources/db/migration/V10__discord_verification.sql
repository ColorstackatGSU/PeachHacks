-- One Discord account per registration and one registration per Discord account. The row
-- stays when a registration leaves ACCEPTED (only the role is taken away), so accepting
-- the person again gives the role back without another code.
create table discord_links (
    registration_id  uuid primary key references registrations (id) on delete cascade,
    discord_user_id  varchar(32) not null,
    discord_username varchar(64),
    linked_at        timestamptz not null default now(),
    constraint uq_discord_links_user unique (discord_user_id)
);

-- The code a Discord account is waiting to enter: one at a time, stored as a SHA-256 of
-- the account id and the code. attempts counts guesses against this code.
create table discord_verification_codes (
    discord_user_id varchar(32) primary key,
    registration_id uuid        not null references registrations (id) on delete cascade,
    code_hash       varchar(64) not null,
    attempts        integer     not null default 0,
    expires_at      timestamptz not null,
    created_at      timestamptz not null default now()
);
