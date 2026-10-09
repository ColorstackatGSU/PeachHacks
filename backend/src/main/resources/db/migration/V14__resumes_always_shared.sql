-- Uploading a resume now means it goes to sponsors; the form says so at the upload and
-- no longer asks separately, so there is no per-resume consent left to record.
alter table registration_resumes drop column sponsor_opt_in;
