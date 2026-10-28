-- Per-user risk limits. A user without a row gets the configured defaults (risk.defaults.*).
create table risk_limits (
    owner                 varchar(64)    primary key,
    max_order_notional    numeric(19, 4) not null,
    max_position_quantity numeric(19, 4) not null,
    updated_at            timestamptz    not null,
    version               bigint         not null default 0
);
