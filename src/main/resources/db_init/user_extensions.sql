CREATE TABLE IF NOT EXISTS public.user_extensions
(
    id uuid NOT NULL,
    version bigint NOT NULL,
    email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    extension character varying(250) COLLATE pg_catalog."default" NOT NULL,
    data jsonb NOT NULL,
    CONSTRAINT user_extensions_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS user_extensions_email
    ON public.user_extensions USING btree
    (email COLLATE pg_catalog."default" ASC NULLS LAST)
    INCLUDE(email)
    TABLESPACE pg_default;
    
CREATE INDEX IF NOT EXISTS user_extensions_email_type
    ON public.user_extensions USING btree
    (email COLLATE pg_catalog."default" ASC NULLS LAST, extension COLLATE pg_catalog."default" ASC NULLS LAST)
    INCLUDE(email, extension)
    TABLESPACE pg_default;