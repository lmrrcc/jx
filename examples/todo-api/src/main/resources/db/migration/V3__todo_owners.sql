-- Existing public-demo rows are retained but never silently assigned to a new identity.
alter table todos add column owner varchar(64) not null default 'legacy-unassigned';
create index todos_owner on todos(owner, created_at, id);
