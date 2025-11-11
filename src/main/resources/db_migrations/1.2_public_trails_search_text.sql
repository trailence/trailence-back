ALTER TABLE public.public_trails ADD COLUMN IF NOT EXISTS search_text_fr tsvector DEFAULT NULL;
ALTER TABLE public.public_trails ADD COLUMN IF NOT EXISTS search_text_en tsvector DEFAULT NULL;

CREATE INDEX IF NOT EXISTS public_trails_search_text_fr
  ON public.public_trails USING GIN (search_text_fr);
CREATE INDEX IF NOT EXISTS public_trails_search_text_en
  ON public.public_trails USING GIN (search_text_en);
  
UPDATE public.public_trails ptu
SET search_text_fr = subquery.text
FROM
 (
	select pt.uuid as uuid, to_tsvector('french',
       COALESCE(
  	     (select unaccent(lower(p.name))
  	      from public.public_trails p
  	      where p.lang = 'fr' and p.uuid = pt.uuid),
	     (select unaccent(lower(p.name_translations ->> 'fr'))
	      from public.public_trails p
	      where p.lang <> 'fr' and p.uuid = pt.uuid),
	     (select unaccent(lower(p.name))
  	      from public.public_trails p
  	      where p.uuid = pt.uuid)
       ) || ' ' || unaccent(lower(pt.location))
     ) AS text
	from public.public_trails pt
  ) AS subquery
WHERE ptu.search_text_fr IS NULL and ptu.uuid = subquery.uuid;

UPDATE public.public_trails ptu
SET search_text_en = subquery.text
FROM
 (
	select pt.uuid as uuid, to_tsvector('english',
       COALESCE(
  	     (select unaccent(lower(p.name))
  	      from public.public_trails p
  	      where p.lang = 'en' and p.uuid = pt.uuid),
	     (select unaccent(lower(p.name_translations ->> 'en'))
	      from public.public_trails p
	      where p.lang <> 'en' and p.uuid = pt.uuid),
	     (select unaccent(lower(p.name))
  	      from public.public_trails p
  	      where p.uuid = pt.uuid)
       ) || ' ' || unaccent(lower(pt.location))
     ) AS text
	from public.public_trails pt
  ) AS subquery
WHERE ptu.search_text_en IS NULL and ptu.uuid = subquery.uuid;
