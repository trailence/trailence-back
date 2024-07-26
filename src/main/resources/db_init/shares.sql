DO
$$
BEGIN
  IF NOT EXISTS (
    SELECT *
    FROM pg_type typ
    INNER JOIN pg_namespace nsp ON nsp.oid = typ.typnamespace
    WHERE nsp.nspname = current_schema()
    AND typ.typname = 'element_type'
  ) THEN
    CREATE TYPE element_type AS ENUM ('TRAIL', 'TAG', 'COLLECTION');
  END IF;
  IF NOT EXISTS (
    SELECT *
    FROM pg_type typ
    INNER JOIN pg_cast ca ON ca.casttarget = typ.oid
    WHERE typ.typname = 'element_type'
  ) THEN
      CREATE CAST (character varying AS public.element_type) WITH INOUT AS ASSIGNMENT;
  END IF;
END;
$$
LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS public.shares
(
	uuid uuid NOT NULL,
	name character varying(50) COLLATE pg_catalog."default" NOT NULL,
    from_email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    to_email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    element_type element_type NOT NULL,
	created_at bigint NOT NULL,
    CONSTRAINT shares_pkey PRIMARY KEY (uuid, from_email)
);

CREATE TABLE IF NOT EXISTS public.share_elements
(
	share_uuid uuid NOT NULL,
	element_uuid uuid NOT NULL,
	owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT share_elements_pkey PRIMARY KEY (share_uuid, element_uuid, owner)
);