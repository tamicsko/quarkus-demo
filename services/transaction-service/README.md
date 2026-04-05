# Transaction Service

Transaction processing microservice. Records financial transactions and calls **account-service** to update balances.

| Property | Value |
|----------|-------|
| Port | 8083 |
| DB Schema | `tx_svc` |
| Base Path | `/api/transactions` |
| Framework | Quarkus 3 / Java 21 |

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/transactions` | List transactions |
| GET | `/api/transactions/{id}` | Get transaction by ID |
| POST | `/api/transactions` | Create transaction |

## Service dependencies

- **account-service** (port 8082) -- called via REST client to debit/credit account balances when a transaction is created.

## Running in dev mode

```bash
./mvnw quarkus:dev -pl services/transaction-service
```

Dev mode connects to the PostgreSQL instance started by `bank-demo-ctl up local-manual`.

## Further reading

- [Architecture Overview](../../docs/architecture/overview.md)
