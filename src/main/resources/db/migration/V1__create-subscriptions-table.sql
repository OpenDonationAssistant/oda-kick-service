create table subscriptions (
  id varchar(255) not null,
  recipient_id varchar(255) not null,
  token_id varchar(255) not null,
	config jsonb not null,
);
