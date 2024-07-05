CREATE TABLE IF NOT EXISTS public.users
(
    email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    password character varying(64) COLLATE pg_catalog."default" NOT NULL,
    created_at bigint NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (email)
)