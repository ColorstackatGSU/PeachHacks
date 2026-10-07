-- A "Continue with Google" round trip in progress. A STATE row is made when the browser
-- leaves for Google and is used up when it comes back; a HANDOFF row then carries the
-- result to the platform page, which trades it for a session. registration_id is null on
-- a handoff for a Google account that matches no accepted application; email says which.
create table google_sign_ins (
    token_hash      varchar(64) primary key,
    kind            varchar(10) not null,
    nonce           varchar(64),
    registration_id uuid references registrations (id) on delete cascade,
    google_subject  varchar(64),
    email           varchar(255),
    expires_at      timestamptz not null,
    constraint ck_google_sign_ins_kind check (kind in ('STATE', 'HANDOFF'))
);
