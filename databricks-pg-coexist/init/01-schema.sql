CREATE TABLE IF NOT EXISTS live_holdings (
    id            BIGSERIAL    PRIMARY KEY,
    account       VARCHAR(20)  NOT NULL,
    symbol        VARCHAR(20)  NOT NULL,
    quantity      NUMERIC(18,4) NOT NULL,
    avg_cost      NUMERIC(18,4) NOT NULL,
    last_updated  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_live_holdings_account ON live_holdings(account);
