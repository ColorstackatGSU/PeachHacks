create table pre_registrations (
    id                uuid primary key,
    first_name        varchar(100) not null,
    last_name         varchar(100) not null,
    email             varchar(255) not null,
    school            varchar(255) not null,
    school_email      varchar(255),
    unsubscribed      boolean      not null default false,
    unsubscribe_token varchar(64)  not null,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    constraint uq_pre_registrations_unsubscribe_token unique (unsubscribe_token)
);

create unique index uq_pre_registrations_email on pre_registrations (lower(email));
create index ix_pre_registrations_school on pre_registrations (school);
create index ix_pre_registrations_created_at on pre_registrations (created_at desc);

create table registrations (
    id                        uuid primary key,
    first_name                varchar(100) not null,
    last_name                 varchar(100) not null,
    age                       integer      not null,
    phone                     varchar(40)  not null,
    email                     varchar(255) not null,
    school                    varchar(255) not null,
    level_of_study            varchar(255) not null,
    country_of_residence      varchar(2)   not null,
    mlh_code_of_conduct       boolean      not null,
    mlh_data_sharing          boolean      not null,
    mlh_email_opt_in          boolean      not null,
    dietary_restrictions      text[]       not null default '{}',
    dietary_details           varchar(1000),
    underrepresented_group    varchar(255),
    gender                    varchar(255),
    gender_self_describe      varchar(255),
    pronouns                  varchar(255),
    pronouns_other            varchar(255),
    race_ethnicity            text[]       not null default '{}',
    race_ethnicity_other      varchar(255),
    sexual_orientation        varchar(255),
    sexual_orientation_other  varchar(255),
    highest_education         varchar(255),
    highest_education_other   varchar(255),
    tshirt_size               varchar(255),
    shipping_line1            varchar(255),
    shipping_line2            varchar(255),
    shipping_city             varchar(255),
    shipping_state            varchar(255),
    shipping_country          varchar(255),
    shipping_postal_code      varchar(255),
    major_field_of_study      varchar(255),
    major_other               varchar(255),
    linkedin_url              varchar(255),
    status                    varchar(20)  not null default 'PENDING',
    unsubscribed              boolean      not null default false,
    unsubscribe_token         varchar(64)  not null,
    created_at                timestamptz  not null default now(),
    constraint uq_registrations_unsubscribe_token unique (unsubscribe_token),
    constraint ck_registrations_age check (age between 13 and 120),
    constraint ck_registrations_status check (status in ('PENDING', 'ACCEPTED', 'WAITLISTED', 'REJECTED'))
);

create unique index uq_registrations_email on registrations (lower(email));
create index ix_registrations_school on registrations (school);
create index ix_registrations_created_at on registrations (created_at desc);

create table admins (
    id            uuid primary key,
    email         varchar(255) not null,
    name          varchar(100) not null,
    password_hash varchar(100) not null,
    created_at    timestamptz  not null default now()
);

create unique index uq_admins_email on admins (lower(email));

create table admin_sessions (
    id         uuid primary key,
    admin_id   uuid        not null references admins (id) on delete cascade,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    constraint uq_admin_sessions_token_hash unique (token_hash)
);

create index ix_admin_sessions_admin_id on admin_sessions (admin_id);

create table settings (
    key        varchar(100) primary key,
    value      varchar(1000) not null,
    updated_at timestamptz   not null default now()
);

insert into settings (key, value) values ('registration_open', 'false');

create table email_campaigns (
    id              uuid primary key,
    subject         varchar(200) not null,
    body            text         not null,
    audience        varchar(40)  not null,
    school          varchar(255),
    recipient_count integer      not null default 0,
    sent_count      integer      not null default 0,
    failed_count    integer      not null default 0,
    status          varchar(20)  not null default 'QUEUED',
    created_by      varchar(255) not null,
    created_at      timestamptz  not null default now(),
    completed_at    timestamptz,
    constraint ck_email_campaigns_status check (status in ('QUEUED', 'SENDING', 'SENT', 'FAILED'))
);

create index ix_email_campaigns_created_at on email_campaigns (created_at desc);
