CREATE TABLE IF NOT EXISTS public.events
(
	id bigint GENERATED ALWAYS AS IDENTITY,
	type character varying(20) COLLATE pg_catalog."default" NOT NULL,
	timestamp bigint NOT NULL,
	data jsonb DEFAULT NULL
);
