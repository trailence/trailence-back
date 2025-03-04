CREATE TABLE IF NOT EXISTS public.plans
(
    name character varying(50) COLLATE pg_catalog."default" NOT NULL,
    collections smallint NOT NULL,
    trails integer NOT NULL,
    tracks integer NOT NULL,
    tracks_size integer NOT NULL,
    photos integer NOT NULL,
    photos_size bigint NOT NULL,
    tags integer NOT NULL,
    trail_tags integer NOT NULL,
    shares smallint NOT NULL,
    CONSTRAINT plans_pkey PRIMARY KEY (name)
)