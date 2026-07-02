create table balances (
    owner varchar(64)    primary key,
    cash  numeric(19, 4) not null
);

-- Seed the demo users with starting cash so they can trade out of the box; users who register later
-- start at zero and must deposit before buying.
insert into balances (owner, cash) values
    ('alice', 1000000),
    ('bob', 1000000),
    ('demo', 1000000);
