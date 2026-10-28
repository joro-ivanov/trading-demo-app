-- Price alerts. An alert is ACTIVE until a price tick crosses its threshold, then TRIGGERED (once).
create table alerts (
    id              uuid           primary key,
    owner           varchar(64)    not null,
    symbol          varchar(10)    not null,
    condition       varchar(5)     not null check (condition in ('ABOVE', 'BELOW')),
    threshold       numeric(19, 4) not null check (threshold > 0),
    status          varchar(9)     not null check (status in ('ACTIVE', 'TRIGGERED')),
    created_at      timestamp with time zone not null,
    triggered_at    timestamp with time zone,
    triggered_price numeric(19, 4),
    version         bigint         not null
);

-- Owner-scoped listing / counting, and the tick-time lookup of ACTIVE alerts for a symbol.
create index alerts_owner_idx on alerts (owner, created_at);
create index alerts_symbol_status_idx on alerts (symbol, status);
