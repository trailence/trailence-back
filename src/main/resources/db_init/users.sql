CREATE TABLE IF NOT EXISTS public.users
(
    email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    password character varying(64) COLLATE pg_catalog."default",
    created_at bigint NOT NULL,
    invalid_attempts smallint NOT NULL DEFAULT 0,
    is_admin boolean NOT NULL DEFAULT FALSE,
    roles jsonb DEFAULT NULL,
    last_password_email bigint DEFAULT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (email)
)