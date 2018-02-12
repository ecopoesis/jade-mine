CREATE TABLE log (
  time timestamp with time zone not null,
  message text not null,
  btc decimal(20, 10) not null,
  usd decimal(20, 10) not null,
  btc_price decimal(20, 10) not null,
  PRIMARY KEY(time)
);