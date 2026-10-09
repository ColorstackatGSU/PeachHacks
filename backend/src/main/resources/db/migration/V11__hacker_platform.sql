-- An accepted hacker's account on the platform. The row appears the first time they sign
-- in (by choosing a password or with Google), so its existence is also what puts them in
-- the hacker directory; listed lets them step out of it again.
create table hacker_accounts (
    registration_id  uuid primary key references registrations (id) on delete cascade,
    password_hash    varchar(100),
    google_subject   varchar(64),
    bio              varchar(280),
    github_url       varchar(200),
    linkedin_url     varchar(200),
    looking_for_team boolean     not null default false,
    listed           boolean     not null default true,
    created_at       timestamptz not null default now(),
    constraint uq_hacker_accounts_google unique (google_subject)
);

create table hacker_sessions (
    token_hash      varchar(64) primary key,
    registration_id uuid        not null references registrations (id) on delete cascade,
    expires_at      timestamptz not null,
    created_at      timestamptz not null default now()
);

create index ix_hacker_sessions_registration_id on hacker_sessions (registration_id);

-- Emailed "choose a password" links, for the first sign-in and for a forgotten password.
create table hacker_password_tokens (
    token_hash      varchar(64) primary key,
    registration_id uuid        not null references registrations (id) on delete cascade,
    expires_at      timestamptz not null,
    used_at         timestamptz,
    created_at      timestamptz not null default now()
);

create table teams (
    id          uuid primary key,
    name        varchar(60) not null,
    description varchar(280),
    owner_id    uuid        not null references registrations (id) on delete cascade,
    created_at  timestamptz not null default now()
);

create unique index uq_teams_name on teams (lower(name));

-- The primary key is the hacker: nobody is on two teams.
create table team_members (
    registration_id uuid primary key references registrations (id) on delete cascade,
    team_id         uuid        not null references teams (id) on delete cascade,
    joined_at       timestamptz not null default now()
);

create index ix_team_members_team_id on team_members (team_id);

create table team_join_requests (
    id              uuid primary key,
    team_id         uuid        not null references teams (id) on delete cascade,
    registration_id uuid        not null references registrations (id) on delete cascade,
    message         varchar(280),
    created_at      timestamptz not null default now(),
    constraint uq_team_join_requests unique (team_id, registration_id)
);

create index ix_team_join_requests_registration_id on team_join_requests (registration_id);
