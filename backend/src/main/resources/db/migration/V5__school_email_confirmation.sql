-- Confirmation belongs to the (personal email, school email) pair, not to a row, so one
-- confirmation covers a pre-registration and the registration that follows it, and a
-- submission with a different school email is unconfirmed without anything being reset.
-- Both addresses are stored lower-cased, like the tables they are matched against.
-- last_sent_at limits how often a link is mailed to the school address.
create table school_email_confirmations (
    id           uuid primary key,
    email        varchar(255) not null,
    school_email varchar(255) not null,
    confirmed_at timestamptz,
    last_sent_at timestamptz,
    created_at   timestamptz  not null default now(),
    constraint uq_school_email_confirmations_pair unique (email, school_email)
);

-- Only the SHA-256 of a link's token is stored. A pair can have several live links (a
-- resend does not break the first email); used_at marks the ones that were clicked.
create table school_email_tokens (
    token_hash      varchar(64) primary key,
    confirmation_id uuid        not null references school_email_confirmations (id) on delete cascade,
    expires_at      timestamptz not null,
    used_at         timestamptz,
    created_at      timestamptz not null default now()
);

create index ix_school_email_tokens_confirmation_id on school_email_tokens (confirmation_id);
