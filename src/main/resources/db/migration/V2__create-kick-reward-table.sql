create table if not exists reward (
    id UUID not null,
    recipient_id varchar(100) not null,
    refresh_token_id UUID not null,
    type varchar(20) not null
);
