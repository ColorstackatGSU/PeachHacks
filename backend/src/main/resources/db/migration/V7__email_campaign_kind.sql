-- An EVENT_UPDATE is logistics for people who registered: always delivered, no unsubscribe
-- link. An ANNOUNCEMENT skips people who unsubscribed and carries the link, which is how
-- every campaign sent before this migration behaved.
alter table email_campaigns
    add column kind varchar(20) not null default 'ANNOUNCEMENT',
    add constraint ck_email_campaigns_kind check (kind in ('EVENT_UPDATE', 'ANNOUNCEMENT'));
