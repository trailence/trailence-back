CREATE TABLE IF NOT EXISTS public.user_subscriptions
(
    uuid uuid NOT NULL,
    user_email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    plan_name character varying(50) COLLATE pg_catalog."default" NOT NULL,
    starts_at bigint NOT NULL,
    ends_at bigint,
    CONSTRAINT user_subscriptions_pkey PRIMARY KEY (uuid)
);

CREATE INDEX IF NOT EXISTS user_subscriptions_email
    ON public.user_subscriptions USING btree
    (user_email COLLATE pg_catalog."default" varchar_ops ASC NULLS LAST)
    INCLUDE(user_email)
    TABLESPACE pg_default;
