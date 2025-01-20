CREATE TABLE IF NOT EXISTS public.user_keys
(
    id uuid NOT NULL,
    email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    public_key bytea NOT NULL,
    created_at bigint NOT NULL,
    last_usage bigint NOT NULL,
    random character(44) COLLATE pg_catalog."default",
    random_expires bigint,
    device_info jsonb NOT NULL,
    invalid_attempts smallint NOT NULL DEFAULT 0,
    deleted_at bigint,
    expires_after bigint DEFAULT 16070400000,
    CONSTRAINT user_keys_pkey PRIMARY KEY (id)
)