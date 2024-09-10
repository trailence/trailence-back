CREATE TABLE IF NOT EXISTS public.files
(
    id bigserial primary key,
    created_at bigint NOT NULL,
    size bigint NOT NULL,
    storage_id character varying(1000),
    tmp boolean NOT NULL
)
