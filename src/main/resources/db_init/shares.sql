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
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    version bigint NOT NULL,
	created_at bigint NOT NULL,
	updated_at bigint NOT NULL,
	name character varying(50) COLLATE pg_catalog."default" NOT NULL,
    element_type element_type NOT NULL,
	include_photos boolean NOT NULL DEFAULT FALSE,
    CONSTRAINT shares_pkey PRIMARY KEY (uuid, owner)
);

CREATE TABLE IF NOT EXISTS public.share_recipients
(
	uuid uuid NOT NULL,
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    recipient character varying(250) COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT share_recipients_pkey PRIMARY KEY (uuid, owner, recipient)
);

CREATE INDEX IF NOT EXISTS share_recipients_owner
    ON public.share_recipients USING btree
    (owner COLLATE pg_catalog."default" ASC NULLS LAST)
    WITH (deduplicate_items=True)
    TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS share_recipients_recipient
    ON public.share_recipients USING btree
    (recipient COLLATE pg_catalog."default" ASC NULLS LAST)
    WITH (deduplicate_items=True)
    TABLESPACE pg_default;

CREATE TABLE IF NOT EXISTS public.share_elements
(
	share_uuid uuid NOT NULL,
	element_uuid uuid NOT NULL,
	owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT share_elements_pkey PRIMARY KEY (share_uuid, element_uuid, owner)
);

CREATE INDEX IF NOT EXISTS share_elements_uuid_owner
    ON public.share_elements USING btree
    (share_uuid ASC NULLS LAST, owner COLLATE pg_catalog."default" ASC NULLS LAST)
    INCLUDE(share_uuid, owner)
    TABLESPACE pg_default;