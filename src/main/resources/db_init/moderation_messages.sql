CREATE TABLE IF NOT EXISTS public.moderation_messages
(
    uuid uuid NOT NULL,
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    author_message character varying(50000) COLLATE pg_catalog."default",
    moderator_message character varying(50000) COLLATE pg_catalog."default",
    CONSTRAINT moderation_message_pkey PRIMARY KEY (uuid, owner)
)
