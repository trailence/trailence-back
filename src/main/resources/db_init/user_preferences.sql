CREATE TABLE IF NOT EXISTS public.user_preferences
(
    email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    lang character(2) DEFAULT NULL,
    elevation_unit smallint DEFAULT NULL,
    distance_unit smallint DEFAULT NULL,
    hour_format smallint DEFAULT NULL,
    date_format smallint DEFAULT NULL,
    theme smallint DEFAULT NULL,
    trace_min_meters integer,
    trace_min_millis bigint,
    offline_map_max_keep_days integer,
    offline_map_max_zoom smallint,
    estimated_base_speed bigint,
    long_break_minimum_duration bigint,
    long_break_maximum_distance bigint,
    CONSTRAINT user_preferences_pkey PRIMARY KEY (email)
)
