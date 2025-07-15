ALTER TABLE public.public_trails DROP COLUMN IF EXISTS alternative_of;
ALTER TABLE public.public_trails ADD COLUMN IF NOT EXISTS lang character varying(2) NOT NULL DEFAULT 'fr';
ALTER TABLE public.public_trails ADD COLUMN IF NOT EXISTS name_translations jsonb DEFAULT NULL;
ALTER TABLE public.public_trails ADD COLUMN IF NOT EXISTS description_translations jsonb DEFAULT NULL;