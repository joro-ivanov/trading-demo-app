-- A user's named watchlists. Names are unique per owner.
create table watchlists (
    id          uuid         primary key,
    owner       varchar(64)  not null,
    name        varchar(40)  not null,
    created_at  timestamptz  not null,
    version     bigint       not null default 0,
    constraint uq_watchlists_owner_name unique (owner, name)
);

create index ix_watchlists_owner on watchlists (owner);

-- The symbols on a watchlist; removed together with their watchlist.
create table watchlist_symbols (
    watchlist_id  uuid         not null references watchlists (id) on delete cascade,
    symbol        varchar(10)  not null,
    added_at      timestamptz  not null,
    primary key (watchlist_id, symbol)
);
