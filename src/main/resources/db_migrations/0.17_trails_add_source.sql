ALTER TABLE public.trails ADD COLUMN IF NOT EXISTS source_type character varying(20) DEFAULT NULL;
ALTER TABLE public.trails ADD COLUMN IF NOT EXISTS source character varying(2000) DEFAULT NULL;
ALTER TABLE public.trails ADD COLUMN IF NOT EXISTS source_date bigint DEFAULT NULL;