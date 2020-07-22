CREATE TYPE sync_status as ENUM(
    'registered',
    'worker_acked',
    'interpreter_acked'
    'synchronized',
    'failed',
    'unregistered'
);

CREATE TYPE coin_family as ENUM(
    'bitcoin'
);

CREATE TABLE account_info(
    account_id UUID PRIMARY KEY,
    xpub VARCHAR NOT NULL,
    coin_family coin_family NOT NULL,
    sync_frequency INTEGER NOT NULL
);

CREATE TABLE account_sync_event(
    id SERIAL PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account_info(account_id),
    sync_id UUID NOT NULL,
    status sync_status NOT NULL,
    blockheight BIGINT NOT NULL,
    error_message VARCHAR,
    updated TIMESTAMP NOT NULL DEFAUlT CURRENT_TIMESTAMP
);

CREATE VIEW account_sync_status AS (
    SELECT DISTINCT ON (account_id)
        account_id,
        xpub,
        coin_family,
        sync_frequency,
        sync_id,
        status,
        blockheight,
        updated
    FROM account_info JOIN account_sync_event USING (account_id)
    ORDER BY account_id, updated DESC
);


