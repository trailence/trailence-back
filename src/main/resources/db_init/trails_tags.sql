CREATE TABLE IF NOT EXISTS public.trails_tags
(
    tag_uuid uuid NOT NULL,
    trail_uuid uuid NOT NULL,
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    created_at bigint NOT NULL,
    CONSTRAINT trails_tags_pkey PRIMARY KEY (tag_uuid, trail_uuid, owner)
);

CREATE INDEX IF NOT EXISTS trails_tags_owner
    ON public.trails_tags USING btree
    (owner COLLATE pg_catalog."default" ASC NULLS LAST)
    WITH (deduplicate_items=True)
    TABLESPACE pg_default;