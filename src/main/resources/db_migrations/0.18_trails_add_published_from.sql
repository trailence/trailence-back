ALTER TABLE public.trails ADD COLUMN IF NOT EXISTS published_from_uuid uuid DEFAULT NULL;
