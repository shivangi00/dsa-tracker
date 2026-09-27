-- V9: recovery codes replace emailed reset links, so the app needs no email service.
--
-- Each account gets a one-time recovery code at sign-up. Only its BCrypt hash is stored:
-- someone who reads this table still can't use a code. Using it sets a new password and
-- issues a fresh code. Accounts created before V9 have none until they make one in Settings.
ALTER TABLE users ADD COLUMN recovery_code_hash VARCHAR(100);

-- Email was only used for reset links. Existing addresses are kept; new accounts don't need one.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

DROP TABLE password_reset_tokens;
