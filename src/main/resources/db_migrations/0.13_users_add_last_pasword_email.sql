ALTER TABLE public.users ADD COLUMN IF NOT EXISTS last_password_email bigint DEFAULT NULL;