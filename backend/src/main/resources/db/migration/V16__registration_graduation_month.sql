-- 1 to 12, asked together with graduation_year; null for registrations made before it.
alter table registrations add column graduation_month integer;
