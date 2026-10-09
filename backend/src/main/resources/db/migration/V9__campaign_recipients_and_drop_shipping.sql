-- One row per person a campaign is going to, written when the campaign is started. The
-- send works through the PENDING rows, so a campaign cut short by a restart carries on
-- with whoever is left and nobody marked SENT is mailed again. The names and the
-- unsubscribe token are copied so the message can be composed again without the audience
-- query. Campaigns sent before this migration have no rows here.
create table campaign_recipients (
    id                bigint generated always as identity primary key,
    campaign_id       uuid         not null references email_campaigns (id) on delete cascade,
    email             varchar(255) not null,
    first_name        varchar(100) not null,
    last_name         varchar(100) not null,
    unsubscribe_token varchar(64),
    status            varchar(20)  not null default 'PENDING',
    sent_at           timestamptz,
    error             varchar(500),
    constraint uq_campaign_recipients_email unique (campaign_id, email),
    constraint ck_campaign_recipients_status check (status in ('PENDING', 'SENT', 'FAILED'))
);

create index ix_campaign_recipients_status on campaign_recipients (campaign_id, status, id);

-- The form never asked for a shipping address.
alter table registrations
    drop column shipping_line1,
    drop column shipping_line2,
    drop column shipping_city,
    drop column shipping_state,
    drop column shipping_country,
    drop column shipping_postal_code;
