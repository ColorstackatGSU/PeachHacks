-- Who PeachBot has already welcomed, so someone who leaves the server and comes back is
-- not welcomed a second time.
create table discord_welcomes (
    discord_user_id varchar(32) primary key,
    welcomed_at     timestamptz not null default now()
);
