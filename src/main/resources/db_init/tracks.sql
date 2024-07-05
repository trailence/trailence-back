CREATE TABLE IF NOT EXISTS public.tracks
(
    uuid uuid NOT NULL,
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    version bigint NOT NULL,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    data bytea NOT NULL,
    CONSTRAINT tracks_pkey PRIMARY KEY (uuid, owner)
)