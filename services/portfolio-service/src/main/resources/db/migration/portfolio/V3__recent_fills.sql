create table recent_fills (
    order_id  varchar(64)              primary key,
    owner     varchar(64)    not null,
    symbol    varchar(20)    not null,
    side      varchar(8)     not null,
    quantity  numeric(19, 4) not null,
    price     numeric(19, 4) not null,
    filled_at timestamp with time zone not null
);

create index idx_recent_fills_owner_filled_at on recent_fills (owner, filled_at desc);
