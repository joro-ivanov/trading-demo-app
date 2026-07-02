create table orders (
    id         uuid           primary key,
    symbol     varchar(20)    not null,
    side       varchar(4)     not null,
    quantity   numeric(19, 4) not null,
    status     varchar(20)    not null,
    created_at timestamptz    not null
);

create index idx_orders_symbol on orders (symbol);
