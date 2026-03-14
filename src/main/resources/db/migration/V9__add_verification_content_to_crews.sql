ALTER TABLE crews ADD COLUMN verification_content VARCHAR(50);
UPDATE crews SET verification_content = SUBSTRING(goal, 1, 50) WHERE verification_content IS NULL;
ALTER TABLE crews ALTER COLUMN verification_content SET NOT NULL;
