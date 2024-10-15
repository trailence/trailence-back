ALTER TABLE public.user_preferences ADD COLUMN IF NOT EXISTS photo_max_pixels integer;
ALTER TABLE public.user_preferences ADD COLUMN IF NOT EXISTS photo_max_quality smallint;
ALTER TABLE public.user_preferences ADD COLUMN IF NOT EXISTS photo_max_size_kb integer;
ALTER TABLE public.user_preferences ADD COLUMN IF NOT EXISTS photo_cache_days integer;