-- Undoes V19: the Hackers page is public by default again, so a new platform account is
-- listed until the hacker turns "Show me on the Hackers page" off.
alter table hacker_accounts alter column listed set default true;
