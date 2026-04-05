# Customer Service

Customer management microservice. Provides CRUD operations for bank customers.

| Property | Value |
|----------|-------|
| Port | 8081 |
| DB Schema | `customer_svc` |
| Base Path | `/api/customers` |
| Framework | Quarkus 3 / Java 21 |

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/customers` | List all customers |
| GET | `/api/customers/{id}` | Get customer by ID |
| POST | `/api/customers` | Create customer |
| PUT | `/api/customers/{id}` | Update customer |
| DELETE | `/api/customers/{id}` | Delete customer |

## Running in dev mode

```bash
./mvnw quarkus:dev -pl services/customer-service
```

Dev mode connects to the PostgreSQL instance started by `bank-demo-ctl up local-manual`.

## Further reading

- [Architecture Overview](../../docs/architecture/overview.md)
