create table if not exists reward (
    id varchar(50) not null,
    account_id UUID not null,
    widget_id UUID not null,  
    type varchar(20) not null
);
