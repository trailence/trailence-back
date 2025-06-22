DO
$$
BEGIN
  IF NOT EXISTS (
    SELECT *
    FROM pg_type typ
    INNER JOIN pg_namespace nsp ON nsp.oid = typ.typnamespace
    WHERE nsp.nspname = current_schema()
    AND typ.typname = 'collection_type'
  ) THEN
    CREATE TYPE collection_type AS ENUM ('MY_TRAILS', 'CUSTOM', 'PUB_DRAFT', 'PUB_SUBMIT', 'PUB_REJECT');
  END IF;
  IF NOT EXISTS (
    SELECT *
    FROM pg_type typ
    INNER JOIN pg_cast ca ON ca.casttarget = typ.oid
    WHERE typ.typname = 'collection_type'
  ) THEN
      CREATE CAST (character varying AS public.collection_type) WITH INOUT AS ASSIGNMENT;
  END IF;
END;
$$
LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS public.collections
(
    uuid uuid NOT NULL,
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    version bigint NOT NULL,
    name character varying(50) NOT NULL,
    type collection_type NOT NULL,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    CONSTRAINT collections_pkey PRIMARY KEY (uuid, owner)
);

CREATE INDEX IF NOT EXISTS collections_type_owner
    ON public.collections USING btree
    (type ASC NULLS LAST, owner COLLATE pg_catalog."default" ASC NULLS LAST)
    INCLUDE(type, owner)
    TABLESPACE pg_default;