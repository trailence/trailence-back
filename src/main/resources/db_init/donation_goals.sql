CREATE TABLE IF NOT EXISTS public.donation_goals
(
    index int NOT NULL,
    type character varying(50) COLLATE pg_catalog."default" NOT NULL,
    amount bigint NOT NULL,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    CONSTRAINT donnation_goals_pkey PRIMARY KEY (index)
);
