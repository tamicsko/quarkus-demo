# Account Service

Bank account management microservice. Provides CRUD operations and balance management for accounts.

| Property | Value |
|----------|-------|
| Port | 8082 |
| DB Schema | `account_svc` |
| Base Path | `/api/accounts` |
| Framework | Quarkus 3 / Java 21 |

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/accounts` | List all accounts |
| GET | `/api/accounts/{id}` | Get account by ID |
| POST | `/api/accounts` | Create account |
| PUT | `/api/accounts/{id}` | Update account |
| DELETE | `/api/accounts/{id}` | Delete account |
| PUT | `/api/accounts/{id}/balance` | Update account balance |

## Running in dev mode

```bash
./mvnw quarkus:dev -pl services/account-service
```

Dev mode connects to the PostgreSQL instance started by `bank-demo-ctl up local-manual`.

## Further reading

- [Architecture Overview](../../docs/architecture/overview.md)
