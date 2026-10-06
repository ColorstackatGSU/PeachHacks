-- Accepting no longer emails the ticket. accepted_at is when the row last became ACCEPTED;
-- acceptance_notified_at is when the acceptance email was handed to the mail provider, so
-- ACCEPTED with a null acceptance_notified_at is the bucket still waiting to be told.
-- Both are null for every other status.
alter table registrations
    add column accepted_at             timestamptz,
    add column acceptance_notified_at  timestamptz;

-- Rows accepted before this migration were emailed at the moment they were accepted.
-- When that happened was never recorded, so the migration time stands in for it and
-- accepted_at stays unknown.
update registrations set acceptance_notified_at = now() where status = 'ACCEPTED';
