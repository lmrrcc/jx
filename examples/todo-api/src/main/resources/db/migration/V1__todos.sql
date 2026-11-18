create table todos (
    id varchar(36) primary key,
    title varchar(200) not null check (length(trim(title)) > 0),
    completed boolean not null,
    version integer not null check (version >= 0),
    created_at timestamp with time zone not null default current_timestamp
);
