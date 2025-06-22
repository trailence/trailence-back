CREATE TABLE IF NOT EXISTS public.notifications
(
    uuid uuid NOT NULL PRIMARY KEY,
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    date bigint NOT NULL,
    is_read boolean NOT NULL DEFAULT FALSE,
    text character varying(1000) NOT NULL,
    text_elements character varying(1000)[]
);

CREATE INDEX IF NOT EXISTS notifications_owner
    ON public.notifications USING btree
    (owner COLLATE pg_catalog."default" varchar_ops ASC NULLS LAST)
    WITH (deduplicate_items=True)
    TABLESPACE pg_default;
