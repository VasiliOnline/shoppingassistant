create table if not exists short_listing_drafts (
    id varchar(64) primary key,
    session_id varchar(64) not null unique,
    user_id bigint not null references auth_users(id) on delete cascade,
    stage varchar(32) not null,
    requested_category_code varchar(64),
    resolved_category_code varchar(64),
    candidate_category jsonb,
    confirmed_category_code varchar(64),
    media jsonb not null,
    predicted_fields jsonb not null,
    confirmed_user_fields jsonb not null,
    missing_required_fields jsonb not null,
    evidence_tasks jsonb not null,
    publish_locals jsonb not null,
    publish_readiness jsonb not null,
    profile_gate jsonb not null,
    identity_signature jsonb,
    last_vision_result jsonb,
    published_offer_id bigint references offers(id) on delete set null,
    lifecycle_status varchar(32),
    expires_at_millis bigint,
    safe_to_exit boolean not null default false,
    revision integer not null default 1,
    created_at bigint not null,
    updated_at bigint not null,
    deleted boolean not null default false
);

create index if not exists idx_short_listing_drafts_user_updated
    on short_listing_drafts(user_id, deleted, updated_at);

create index if not exists idx_short_listing_drafts_user_stage
    on short_listing_drafts(user_id, stage);

create index if not exists idx_short_listing_drafts_user_offer
    on short_listing_drafts(user_id, published_offer_id);

create index if not exists idx_short_listing_drafts_user_category
    on short_listing_drafts(user_id, resolved_category_code);

create index if not exists idx_short_listing_drafts_user_lifecycle
    on short_listing_drafts(user_id, lifecycle_status);
