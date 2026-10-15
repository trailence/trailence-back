ALTER TABLE public.trails ADD COLUMN IF NOT EXISTS date_tz_offset smallint DEFAULT NULL;
ALTER TABLE public.public_trails ADD COLUMN IF NOT EXISTS date_tz_offset smallint DEFAULT NULL;
ALTER TABLE public.photos ADD COLUMN IF NOT EXISTS date_taken_tz_offset smallint DEFAULT NULL;
ALTER TABLE public.public_photos ADD COLUMN IF NOT EXISTS date_tz_offset smallint DEFAULT NULL;
