-- Optimistic locking for positions: a concurrent read-modify-write now fails instead of silently
-- overwriting another update (previously a price tick could restore a stale quantity over a fill).
alter table positions add column version bigint not null default 0;
