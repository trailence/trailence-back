ALTER TABLE public.user_keys ADD COLUMN IF NOT EXISTS deleted_at bigint;
ALTER TABLE public.user_keys ADD COLUMN IF NOT EXISTS expires_after bigint DEFAULT 16070400000;