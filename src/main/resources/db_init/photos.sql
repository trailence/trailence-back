CREATE TABLE IF NOT EXISTS public.photos
(
    uuid uuid NOT NULL,
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    version bigint NOT NULL,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    file_id bigint NOT NULL,
    trail_uuid uuid NOT NULL,
    description character varying(5000),
    date_taken bigint,
    latitude bigint,
    longitude bigint,
    is_cover boolean NOT NULL DEFAULT FALSE,
    index integer NOT NULL DEFAULT 1,
    CONSTRAINT photos_pkey PRIMARY KEY (uuid, owner)
)
