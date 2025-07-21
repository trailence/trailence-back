CREATE TABLE IF NOT EXISTS public.public_trail_feedback
(
	uuid uuid NOT NULL PRIMARY KEY,
    public_trail_uuid uuid NOT NULL,
    email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    date bigint NOT NULL,
    rate smallint DEFAULT NULL,
    comment character varying(50000) COLLATE pg_catalog."default" DEFAULT NULL,
    reviewed boolean DEFAULT FALSE
);
