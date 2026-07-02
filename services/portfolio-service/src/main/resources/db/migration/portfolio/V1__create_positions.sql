create table positions (
    owner        varchar(64)    not null,
    symbol       varchar(20)    not null,
    quantity     numeric(19, 4) not null,
    avg_price    numeric(19, 4) not null,
    market_price numeric(19, 4),
    primary key (owner, symbol)
);

create index idx_positions_symbol on positions (symbol);
