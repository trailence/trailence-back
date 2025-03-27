CREATE TABLE IF NOT EXISTS public.share_emails
(
	share_uuid uuid NOT NULL,
    from_email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    to_email character varying(250) COLLATE pg_catalog."default" NOT NULL,
	sent_at bigint NOT NULL,
    CONSTRAINT share_emails_pkey PRIMARY KEY (share_uuid, from_email, to_email)
);