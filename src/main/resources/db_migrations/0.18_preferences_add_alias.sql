ALTER TABLE public.user_preferences ADD COLUMN IF NOT EXISTS alias character varying(25) NOT NULL DEFAULT '';
