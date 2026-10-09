-- The year the hacker expects to graduate. Required on the form from now on; null for
-- registrations made before it was asked.
alter table registrations add column graduation_year integer;
