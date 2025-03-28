CREATE TABLE IF NOT EXISTS public.contact_messages
(
    uuid uuid NOT NULL,
    email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    message_type character varying(100) COLLATE pg_catalog."default" NOT NULL,
    message_text text COLLATE pg_catalog."default" NOT NULL,
    sent_at bigint NOT NULL,
    is_read boolean NOT NULL DEFAULT FALSE,
    CONSTRAINT contact_messages_pkey PRIMARY KEY (uuid)
);
