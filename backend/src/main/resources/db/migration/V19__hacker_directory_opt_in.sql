-- The Hackers page is opt-in: a new platform account starts unlisted, and a hacker appears
-- only after ticking "Show me on the Hackers page" and saving their card. Existing rows keep
-- whatever they have.
alter table hacker_accounts alter column listed set default false;
