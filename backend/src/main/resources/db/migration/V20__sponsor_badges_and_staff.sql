-- A sponsor is not a person in the system: their card is bound as a sponsor badge with no
-- registration, so a tap can only say "sponsor". The two partial unique indexes stay as
-- they are; a null registration_id never conflicts in the per-registration one.
alter table badges
    alter column registration_id drop not null,
    add column kind varchar(20) not null default 'HACKER',
    add constraint ck_badges_kind check (kind in ('HACKER', 'SPONSOR')),
    add constraint ck_badges_registration_by_kind check ((registration_id is not null) = (kind = 'HACKER'));

create index ix_badges_kind_bound_at on badges (kind, bound_at desc) where revoked_at is null;

-- E-board and other staff are registrations an admin marks by hand, so that their badge
-- says "staff". It changes nothing else about the registration.
alter table registrations add column staff boolean not null default false;
