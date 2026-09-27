-- "Method used" is now required.
-- Never edit V1: Flyway has already run it on your database and stores its checksum.
-- Changes always go in a new, higher-numbered file like this one.

-- Rows logged before this change may have no method; give them a placeholder first,
-- otherwise adding NOT NULL would fail.
UPDATE problems
SET method = '(not recorded)'
WHERE method IS NULL OR btrim(method) = '';

ALTER TABLE problems ALTER COLUMN method SET NOT NULL;
ALTER TABLE problems ADD CONSTRAINT problems_method_not_blank CHECK (btrim(method) <> '');
