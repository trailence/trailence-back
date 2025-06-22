DO
$$
BEGIN
  IF NOT EXISTS (
	SELECT *
	FROM pg_type typ
	INNER JOIN pg_namespace nsp ON nsp.oid = typ.typnamespace
	JOIN pg_enum e on typ.oid = e.enumtypid
	WHERE nsp.nspname = current_schema()
	AND typ.typname = 'collection_type'
	AND e.enumlabel = 'PUB_DRAFT'
  ) THEN
    ALTER TYPE collection_type ADD VALUE 'PUB_DRAFT';
    ALTER TYPE collection_type ADD VALUE 'PUB_SUBMIT';
    ALTER TYPE collection_type ADD VALUE 'PUB_REJECT';
  END IF;
END;
$$
LANGUAGE plpgsql;