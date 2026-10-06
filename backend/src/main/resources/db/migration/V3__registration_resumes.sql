-- The file lives in its own table so that reading registrations never reads resume bytes.
-- sponsor_opt_in sits beside the file because consent to share is meaningless without one
-- and must disappear with it.
create table registration_resumes (
    registration_id uuid primary key references registrations (id) on delete cascade,
    file_name       varchar(255) not null,
    size_bytes      integer      not null,
    content         bytea        not null,
    sponsor_opt_in  boolean      not null default false,
    uploaded_at     timestamptz  not null default now(),
    constraint ck_registration_resumes_size check (size_bytes > 0)
);

-- PDFs are already compressed; skip the attempt to compress them again on every write.
alter table registration_resumes alter column content set storage external;
