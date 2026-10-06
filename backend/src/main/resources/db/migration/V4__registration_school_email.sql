-- Nullable because registrations made before the form asked for it have none; the
-- application requires it on new submissions.
alter table registrations add column school_email varchar(255);
