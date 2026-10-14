-- The form asks for a date of birth; age stays as the age on the event's first day, so
-- eligibility checks keep reading it. Registrations made before the change have no date.
alter table registrations add column date_of_birth date;

-- How many times a link was mailed again on its own after the previous one expired.
alter table school_email_confirmations add column automatic_resends integer not null default 0;
