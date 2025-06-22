CREATE TABLE IF NOT EXISTS public.public_trails
(
    uuid uuid NOT NULL PRIMARY KEY,
    author character varying(250) COLLATE pg_catalog."default" NOT NULL,
    author_uuid uuid,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    slug character varying(200) COLLATE pg_catalog."default" NOT NULL UNIQUE,
    name character varying(200) NOT NULL,
    description character varying(50000) NOT NULL,
    location character varying(100) NOT NULL,
    date bigint NOT NULL,
    distance bigint NOT NULL,
    positive_elevation int,
    negative_elevation int,
    highest_altitude int,
    lowest_altitude int,
    duration bigint,
    breaks_duration bigint NOT NULL,
    estimated_duration bigint NOT NULL,
    loop_type character varying(2) NOT NULL,
    activity character varying(20) NOT NULL,
    bounds box NOT NULL,
    tile_zoom1 int NOT NULL,
    tile_zoom2 int NOT NULL,
    tile_zoom3 int NOT NULL,
    tile_zoom4 int NOT NULL,
    tile_zoom5 int NOT NULL,
    tile_zoom6 int NOT NULL,
    tile_zoom7 int NOT NULL,
    tile_zoom8 int NOT NULL,
    tile_zoom9 int NOT NULL,
    tile_zoom10 int NOT NULL,
    simplified_path int[] NOT NULL,
    nb_rate0 bigint DEFAULT 0,
    nb_rate1 bigint DEFAULT 0,
    nb_rate2 bigint DEFAULT 0,
    nb_rate3 bigint DEFAULT 0,
    nb_rate4 bigint DEFAULT 0,
    nb_rate5 bigint DEFAULT 0,
    alternative_of uuid
);

CREATE INDEX IF NOT EXISTS public_trails_bounds
    ON public.public_trails USING gist
    (bounds box_ops);
CREATE INDEX IF NOT EXISTS public_trails_zoom1
    ON public.public_trails USING btree
    (tile_zoom1);
CREATE INDEX IF NOT EXISTS public_trails_zoom2
    ON public.public_trails USING btree
    (tile_zoom2);
CREATE INDEX IF NOT EXISTS public_trails_zoom3
    ON public.public_trails USING btree
    (tile_zoom3);
CREATE INDEX IF NOT EXISTS public_trails_zoom4
    ON public.public_trails USING btree
    (tile_zoom4);
CREATE INDEX IF NOT EXISTS public_trails_zoom5
    ON public.public_trails USING btree
    (tile_zoom5);
CREATE INDEX IF NOT EXISTS public_trails_zoom6
    ON public.public_trails USING btree
    (tile_zoom6);
CREATE INDEX IF NOT EXISTS public_trails_zoom7
    ON public.public_trails USING btree
    (tile_zoom7);
CREATE INDEX IF NOT EXISTS public_trails_zoom8
    ON public.public_trails USING btree
    (tile_zoom8);
CREATE INDEX IF NOT EXISTS public_trails_zoom9
    ON public.public_trails USING btree
    (tile_zoom9);
CREATE INDEX IF NOT EXISTS public_trails_zoom10
    ON public.public_trails USING btree
    (tile_zoom10);

    
CREATE TABLE IF NOT EXISTS public.public_tracks
(
	trail_uuid uuid NOT NULL PRIMARY KEY,
	data bytea NOT NULL
);

CREATE TABLE IF NOT EXISTS public.public_photos
(
	uuid uuid NOT NULL PRIMARY KEY,
	trail_uuid uuid NOT NULL,
	file_id bigint NOT NULL,
	description character varying(5000),
    date bigint,
    latitude bigint,
    longitude bigint,
    index integer NOT NULL
);

CREATE INDEX IF NOT EXISTS public_photos_trail_uuid
    ON public.public_photos USING btree
    (trail_uuid);