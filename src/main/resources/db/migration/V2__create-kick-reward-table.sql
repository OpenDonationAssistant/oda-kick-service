create table if not exists reward (
    id varchar(50) not null,
    recipient_id UUID not null,
    refresh_token_id UUID not null,
    type varchar(20) not null
);
