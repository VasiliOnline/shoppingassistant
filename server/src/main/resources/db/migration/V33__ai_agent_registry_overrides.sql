create table if not exists ai_agent_registry_overrides (
    flow varchar(64) primary key,
    enabled boolean not null,
    route_version varchar(128) not null,
    contract_name varchar(128) not null,
    contract_version varchar(128) not null,
    kill_switch_reason text null,
    updated_at_ms bigint not null,
    updated_by varchar(255) null,
    note text null
);
