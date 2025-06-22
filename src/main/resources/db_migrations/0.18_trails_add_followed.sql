ALTER TABLE public.trails ADD COLUMN IF NOT EXISTS followed_uuid uuid DEFAULT NULL;
ALTER TABLE public.trails ADD COLUMN IF NOT EXISTS followed_owner character varying(250) DEFAULT NULL;
ALTER TABLE public.trails ADD COLUMN IF NOT EXISTS followed_url character varying(2000) DEFAULT NULL;