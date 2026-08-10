CREATE TABLE IF NOT EXISTS public.shared_collections
(
    uuid uuid NOT NULL PRIMARY KEY,
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL
);

CREATE TABLE IF NOT EXISTS public.shared_collection_members (
    shared_collection_uuid uuid NOT NULL,
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    uuid uuid NOT NULL,
    name varchar(50) NOT NULL,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    version bigint NOT NULL,

    PRIMARY KEY (shared_collection_uuid, owner),
    UNIQUE (owner, uuid)
);

CREATE INDEX IF NOT EXISTS shared_collection_members_email
ON public.shared_collection_members (owner);