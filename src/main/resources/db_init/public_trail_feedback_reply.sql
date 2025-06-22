CREATE TABLE IF NOT EXISTS public.public_trail_feedback_reply
(
	uuid uuid NOT NULL PRIMARY KEY,
    reply_to uuid NOT NULL,
    email character varying(250) COLLATE pg_catalog."default" NOT NULL,
    date bigint NOT NULL,
    comment character varying(50000) COLLATE pg_catalog."default" NOT NULL
);

CREATE INDEX IF NOT EXISTS public_trail_feedback_reply_to
    ON public.public_trail_feedback_reply USING btree
    (reply_to);