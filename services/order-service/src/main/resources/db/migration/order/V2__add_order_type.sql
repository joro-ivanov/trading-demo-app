-- Order types: MARKET (default for existing rows), LIMIT (limit_price), STOP (stop_price).
alter table orders add column type        varchar(10)    not null default 'MARKET';
alter table orders add column limit_price  numeric(19, 4);
alter table orders add column stop_price   numeric(19, 4);
