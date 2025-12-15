CREATE TABLE IF NOT EXISTS public.trail_links
(
	uuid uuid NOT NULL PRIMARY KEY,
	link_key1 uuid NOT NULL,
	link_key2 uuid NOT NULL, 
    author character varying(250) COLLATE pg_catalog."default" NOT NULL,
    author_uuid uuid NOT NULL,
    created_at bigint NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS trail_link_author_uuid
  ON public.trail_links USING btree
  (author, author_uuid);
  
CREATE INDEX IF NOT EXISTS trail_link_author
  ON public.trail_links USING btree
  (author);