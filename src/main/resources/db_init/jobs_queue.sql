CREATE TABLE IF NOT EXISTS public.jobs_queue
(
	id uuid NOT NULL,
	type character varying(20) COLLATE pg_catalog."default" NOT NULL,
	priority int NOT NULL,
	start_at bigint NOT NULL,
	next_retry_at bigint NOT NULL,
	retry int NOT NULL,
	expires_at bigint NOT NULL,
	data jsonb NOT NULL,
    CONSTRAINT jobs_queue_pkey PRIMARY KEY (id)
)