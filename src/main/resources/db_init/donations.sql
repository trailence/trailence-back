CREATE TABLE IF NOT EXISTS public.donations
(
    uuid uuid NOT NULL,
    platform character varying(50) COLLATE pg_catalog."default" NOT NULL,
    platform_id character varying(200) COLLATE pg_catalog."default" NOT NULL,
    type character varying(50) COLLATE pg_catalog."default" NOT NULL,
    timestamp bigint NOT NULL,
    amount bigint NOT NULL,
    details character varying(10000) COLLATE pg_catalog."default",
    CONSTRAINT donnations_pkey PRIMARY KEY (uuid)
);

CREATE INDEX IF NOT EXISTS donations_platform_id
    ON public.donations USING btree
    (platform_id COLLATE pg_catalog."default" varchar_ops ASC NULLS LAST, platform COLLATE pg_catalog."default" varchar_ops ASC NULLS LAST)
    INCLUDE(platform_id, platform)
    TABLESPACE pg_default;
