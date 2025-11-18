CREATE TABLE IF NOT EXISTS public.moderation_messages
(
    uuid uuid NOT NULL,
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    author_message character varying(50000) COLLATE pg_catalog."default",
    moderator_message character varying(50000) COLLATE pg_catalog."default",
    message_type character varying(10) COLLATE pg_catalog."default" NOT NULL DEFAULT 'publish',
    CONSTRAINT moderation_message_pkey PRIMARY KEY (uuid, owner)
)
