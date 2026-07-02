-- The user who placed the order (JWT subject). Existing rows predate ownership → 'unknown'.
alter table orders add column owner varchar(64) not null default 'unknown';
create index idx_orders_owner on orders (owner);
