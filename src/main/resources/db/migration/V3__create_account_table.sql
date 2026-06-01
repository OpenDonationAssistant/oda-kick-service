create table if not exists accounts (
    id UUID not null,
    kick_id varchar(36) not null,
    username varchar(100) not null,
    recipient_id varchar(100) not null,
    refresh_token_id UUID not null
);
