CREATE TABLE IF NOT EXISTS trades (
    id         BIGINT       PRIMARY KEY,
    account    VARCHAR(20)  NOT NULL,
    symbol     VARCHAR(20)  NOT NULL,
    side       VARCHAR(4)   NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity   NUMERIC(18,4) NOT NULL,
    price      NUMERIC(18,4) NOT NULL,
    exec_time  TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_trades_account ON trades(account);
