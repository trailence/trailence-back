CREATE TABLE IF NOT EXISTS public.tags
(
    uuid uuid NOT NULL,
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    version bigint NOT NULL,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    name character varying(50),
    collection_uuid uuid NOT NULL,
    parent_uuid uuid DEFAULT NULL,
    CONSTRAINT tags_pkey PRIMARY KEY (uuid, owner)
)
