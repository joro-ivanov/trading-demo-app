alter table recent_fills add column sequence_id bigserial;

alter table recent_fills alter column sequence_id set not null;

drop index idx_recent_fills_owner_filled_at;

create index idx_recent_fills_owner_filled_at_sequence
    on recent_fills (owner, filled_at desc, sequence_id desc);
