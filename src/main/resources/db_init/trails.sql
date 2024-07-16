CREATE TABLE IF NOT EXISTS public.trails
(
    uuid uuid NOT NULL,
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    version bigint NOT NULL,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    name character varying(200),
    description character varying(50000),
    location character varying(100),
    original_track_uuid uuid NOT NULL,
    current_track_uuid uuid NOT NULL,
    collection_uuid uuid NOT NULL,
    CONSTRAINT trails_pkey PRIMARY KEY (uuid, owner)
)
