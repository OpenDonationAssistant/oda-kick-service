create table if not exists reward (
    id varchar(36) primary key,
    recipient_id varchar(255) not null,
    name varchar(255) not null,
    description varchar(255) not null,
    created_at timestamp default current_timestamp,
    updated_at timestamp default current_timestamp
);
