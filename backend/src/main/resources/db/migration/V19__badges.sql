alter table admins
    drop constraint ck_admins_role,
    add constraint ck_admins_role check (role in ('ADMIN', 'VOLUNTEER', 'LOOKUP'));

-- An NFC card is known only by its UID, stored as upper-case hex. bound_by and revoked_by
-- are display names at the time, like check_ins.checked_in_by. Revoked rows are history.
create table badges (
    id              uuid primary key,
    uid             varchar(20)  not null,
    registration_id uuid         not null references registrations (id) on delete cascade,
    bound_at        timestamptz  not null,
    bound_by        varchar(100) not null,
    revoked_at      timestamptz,
    revoked_by      varchar(100)
);

-- One active binding per card and one active badge per registration. These two indexes
-- decide when two volunteers bind at the same moment.
create unique index uq_badges_active_uid on badges (uid) where revoked_at is null;
create unique index uq_badges_active_registration on badges (registration_id) where revoked_at is null;

create index ix_badges_uid on badges (uid);
