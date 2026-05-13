alter table offers
    add column if not exists publication_state varchar(32) not null default 'LIVE';

update offers
set publication_state = 'LIVE'
where publication_state is null
   or publication_state = '';

create index if not exists idx_offers_publication_state
    on offers(publication_state);

create index if not exists idx_offers_status_publication_state
    on offers(status, publication_state);

create table if not exists local_offer_sessions (
    session_id varchar(64) primary key,
    user_id bigint not null references auth_users(id) on delete cascade,
    current_draft_id varchar(64) unique references short_listing_drafts(id) on delete set null,
    state varchar(32) not null,
    created_at bigint not null,
    updated_at bigint not null
);

create index if not exists idx_local_offer_sessions_user_updated
    on local_offer_sessions(user_id, updated_at desc);

create table if not exists local_offer_geo_snapshots (
    id varchar(64) primary key,
    session_id varchar(64) not null references local_offer_sessions(session_id) on delete cascade,
    user_id bigint not null references auth_users(id) on delete cascade,
    draft_id varchar(64) references short_listing_drafts(id) on delete set null,
    consent_state varchar(16) not null,
    status varchar(32) not null,
    captured_at_millis bigint not null,
    expires_at_millis bigint not null,
    accuracy_meters double precision not null,
    country_code varchar(8) not null,
    admin_area varchar(255),
    city varchar(255) not null,
    lat double precision,
    lon double precision,
    source varchar(32) not null,
    created_at bigint not null,
    updated_at bigint not null,
    deleted boolean not null default false
);

create index if not exists idx_local_offer_geo_snapshots_user_session
    on local_offer_geo_snapshots(user_id, session_id, deleted, updated_at desc);

create index if not exists idx_local_offer_geo_snapshots_user_draft
    on local_offer_geo_snapshots(user_id, draft_id, deleted, updated_at desc);

create table if not exists local_offer_publish_commands (
    id varchar(64) primary key,
    draft_id varchar(64) not null references short_listing_drafts(id) on delete cascade,
    user_id bigint not null references auth_users(id) on delete cascade,
    revision integer not null,
    effective_spec_version varchar(128) not null,
    geo_snapshot_id varchar(64) not null references local_offer_geo_snapshots(id) on delete restrict,
    state varchar(32) not null,
    offer_id bigint references offers(id) on delete set null,
    failure_code varchar(128),
    created_at bigint not null,
    updated_at bigint not null
);

create index if not exists idx_local_offer_publish_commands_user_draft
    on local_offer_publish_commands(user_id, draft_id, updated_at desc);

create index if not exists idx_local_offer_publish_commands_user_state
    on local_offer_publish_commands(user_id, state, updated_at desc);
