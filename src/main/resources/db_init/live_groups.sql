CREATE TABLE IF NOT EXISTS public.live_groups
(
    uuid uuid NOT NULL PRIMARY KEY,
    owner character varying(250) COLLATE pg_catalog."default" NOT NULL,
    slug character varying(128) COLLATE pg_catalog."default" NOT NULL UNIQUE,
    name character varying(30) NOT NULL,
    started_at bigint NOT NULL,
    trail_owner character varying(250) COLLATE pg_catalog."default" DEFAULT NULL,
    trail_uuid character varying(1024) COLLATE pg_catalog."default" DEFAULT NULL,
    trail_shared boolean NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS public.live_group_members
(
    uuid uuid NOT NULL PRIMARY KEY,
    group_uuid uuid NOT NULL,
    member_id character varying(250) COLLATE pg_catalog."default" NOT NULL,
    member_name character varying(25) NOT NULL,
    join_at bigint,
    last_position_lat bigint DEFAULT NULL,
    last_position_lon bigint DEFAULT NULL,
    last_position_at bigint DEFAULT NULL
);

CREATE INDEX IF NOT EXISTS live_group_members_group_uuid
    ON public.live_group_members USING btree
    (group_uuid);
    
CREATE INDEX IF NOT EXISTS live_group_members_id
    ON public.live_group_members USING btree
    (member_id);
    
CREATE UNIQUE INDEX IF NOT EXISTS live_group_members_unique_group_member
	ON public.live_group_members USING btree
	(group_uuid, member_id);
