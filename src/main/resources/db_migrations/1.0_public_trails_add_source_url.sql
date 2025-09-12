ALTER TABLE public.public_trails ADD COLUMN IF NOT EXISTS source_url character varying(500) COLLATE pg_catalog."default";
