alter table admins
    add column role varchar(20) not null default 'ADMIN',
    add constraint ck_admins_role check (role in ('ADMIN', 'VOLUNTEER'));

alter table registrations add column ticket_token varchar(64);

-- Two random UUIDs (244 random bits) for rows that existed before tickets; new rows get
-- their token from the application.
update registrations
set ticket_token = replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');

alter table registrations
    alter column ticket_token set not null,
    add constraint uq_registrations_ticket_token unique (ticket_token);

create table events (
    id         uuid primary key,
    name       varchar(120) not null,
    starts_at  timestamptz,
    general    boolean      not null default false,
    created_at timestamptz  not null default now()
);

create unique index uq_events_name on events (lower(name));
-- At most one built-in general check-in event.
create unique index uq_events_general on events (general) where general;

insert into events (id, name, general) values (gen_random_uuid(), 'General check-in', true);

-- checked_in_by is the display name at the time of check-in, not a foreign key, so the
-- record survives the volunteer's account being removed after the event.
create table check_ins (
    id              uuid primary key,
    registration_id uuid         not null references registrations (id) on delete cascade,
    event_id        uuid         not null references events (id) on delete cascade,
    checked_in_at   timestamptz  not null default now(),
    checked_in_by   varchar(100) not null,
    constraint uq_check_ins_registration_event unique (registration_id, event_id)
);

create index ix_check_ins_event_id on check_ins (event_id);
