CREATE TABLE IF NOT EXISTS public.user_selection
(
    email character varying(250) COLLATE pg_catalog."default" NOT NULL PRIMARY KEY,
    selection jsonb NOT NULL
)