ALTER TABLE public.jobs_queue ADD COLUMN IF NOT EXISTS priority int NOT NULL DEFAULT 0;