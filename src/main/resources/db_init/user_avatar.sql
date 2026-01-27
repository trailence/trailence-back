CREATE TABLE IF NOT EXISTS public.user_avatar
(
    email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    version bigint DEFAULT 1,
    public_uuid uuid DEFAULT NULL,
    current_file_id bigint DEFAULT NULL,
    current_public boolean NOT NULL DEFAULT FALSE,
    new_file_id bigint DEFAULT NULL,
    new_public boolean NOT NULL DEFAULT FALSE,
    new_file_submitted_at bigint DEFAULT NULL,
    CONSTRAINT user_avatar_pkey PRIMARY KEY (email)
);

CREATE UNIQUE INDEX IF NOT EXISTS avatar_public_uuid
    ON public.user_avatar USING btree
    (public_uuid);
