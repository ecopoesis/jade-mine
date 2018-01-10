CREATE TABLE ohlc (
  exchange varchar(255) not null,
  time timestamp with time zone not null,
  low decimal(20, 10) not null,
  high decimal(20, 10) not null,
  open decimal(20, 10) not null,
  close decimal(20, 10) not null,
  volume decimal(20, 10) not null,
  PRIMARY KEY(exchange, time)
);