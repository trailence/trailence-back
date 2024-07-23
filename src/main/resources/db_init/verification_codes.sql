CREATE TABLE IF NOT EXISTS public.verification_codes
(
    code character varying(6) COLLATE pg_catalog."default" NOT NULL,
    type character varying(20) COLLATE pg_catalog."default" NOT NULL,
    key character varying(250) COLLATE pg_catalog."default" NOT NULL,
    expires_at bigint NOT NULL,
    data jsonb NOT NULL,
    CONSTRAINT verification_codes_pkey PRIMARY KEY (code)
)