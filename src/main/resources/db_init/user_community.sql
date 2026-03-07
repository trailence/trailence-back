CREATE TABLE IF NOT EXISTS public.user_community (
	email character varying(250) COLLATE pg_catalog."default" NOT NULL,
	public_uuid uuid NOT NULL DEFAULT gen_random_uuid(),
	nb_publications int DEFAULT 0,
	nb_comments int DEFAULT 0,
	nb_rates int DEFAULT 0,
	CONSTRAINT user_community_pkey PRIMARY KEY (email)
);

CREATE UNIQUE INDEX IF NOT EXISTS user_community_public_uuid
    ON public.user_community USING btree
    (public_uuid);
