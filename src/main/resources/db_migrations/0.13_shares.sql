DO
$$
BEGIN
  IF EXISTS (
    SELECT *
    FROM information_schema.columns
	WHERE table_schema = current_schema()
	AND table_name = 'shares'
	AND column_name = 'from_email'
  ) THEN
  	ALTER TABLE shares RENAME COLUMN from_email TO owner;
  	ALTER TABLE shares ADD COLUMN updated_at bigint;
  	ALTER TABLE shares ADD COLUMN version bigint;
  	UPDATE shares SET updated_at = created_at, version = 1;
  	ALTER TABLE shares ALTER COLUMN updated_at SET NOT NULL;
  	ALTER TABLE shares ALTER COLUMN version SET NOT NULL;
  	INSERT INTO share_recipients (uuid,owner,recipient) SELECT uuid,owner,to_email as recipient FROM shares;
  	ALTER TABLE shares DROP COLUMN to_email;
  END IF;
END;
$$
LANGUAGE plpgsql;
