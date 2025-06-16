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
    date bigint,
    loop_type character varying(2),
    activity character varying(20),
    source_type character varying(20),
    source character varying(2000),
    source_date bigint,
    original_track_uuid uuid NOT NULL,
    current_track_uuid uuid NOT NULL,
    collection_uuid uuid NOT NULL,
    CONSTRAINT trails_pkey PRIMARY KEY (uuid, owner)
)
