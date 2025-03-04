CREATE TABLE IF NOT EXISTS public.user_quotas
(
    email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    collections_used smallint NOT NULL DEFAULT 0,
    collections_max smallint NOT NULL DEFAULT 0,
    trails_used integer NOT NULL DEFAULT 0,
    trails_max integer NOT NULL DEFAULT 0,
    tracks_used integer NOT NULL DEFAULT 0,
    tracks_max integer NOT NULL DEFAULT 0,
    tracks_size_used integer NOT NULL DEFAULT 0,
    tracks_size_max integer NOT NULL DEFAULT 0,
    photos_used integer NOT NULL DEFAULT 0,
    photos_max integer NOT NULL DEFAULT 0,
    photos_size_used bigint NOT NULL DEFAULT 0,
    photos_size_max bigint NOT NULL DEFAULT 0,
    tags_used integer NOT NULL DEFAULT 0,
    tags_max integer NOT NULL DEFAULT 0,
    trail_tags_used integer NOT NULL DEFAULT 0,
    trail_tags_max integer NOT NULL DEFAULT 0,
    shares_used smallint NOT NULL DEFAULT 0,
    shares_max smallint NOT NULL DEFAULT 0,
    CONSTRAINT user_quotas_pkey PRIMARY KEY (email)
)