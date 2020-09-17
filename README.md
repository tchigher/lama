# Ledger Account Manager API

Synchronization and management of account states (transactions, balance, ...) across Blockchain protocols.

## How account synchronization works?

![](./excalidraw/sync-overview.png)

## Account manager

The account manager handles account registration and unregistration and emits events to the dedicated coin worker.
Please refer to the [account manager README][account-manager] for more details on how it works.

### Run through docker (recommended)

Install [docker][docker] then run `docker-compose up`.

This will create a PostgreSql, a RabbitMQ, a Redis and the latest published image of the lama account manager.

This will also apply migration on the PostgreSql database through [flyway][flyway].

Please have a look on `docker-compose.yml` file for more details on the configuration.

### Run manually

Please refer to the [getting started][account-manager-getting-started] section of the account manager README.

## Coin integration

TODO

[docker]: https://docs.docker.com/get-docker/
[flyway]: https://flywaydb.org/
[account-manager]: https://github.com/LedgerHQ/lama/tree/master/account-manager
[account-manager-getting-started]: https://github.com/LedgerHQ/lama/blob/master/account-manager/README.md#getting-started
