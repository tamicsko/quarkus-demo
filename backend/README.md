# Backend (BFF)

Backend for Frontend -- API gateway that aggregates the three domain microservices into a unified API consumed by the Angular frontend.

| Property | Value |
|----------|-------|
| Port | 8080 |
| Framework | Quarkus 3 / Java 21 |
| Swagger UI | http://localhost:8080/q/swagger-ui |

## Aggregated services

| Service | Port | Purpose |
|---------|------|---------|
| customer-service | 8081 | Customer CRUD |
| account-service | 8082 | Account CRUD + balance |
| transaction-service | 8083 | Transaction processing |

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/clients` | List clients (customers + accounts) |
| GET | `/api/clients/{id}` | Get client detail |
| GET | `/api/clients/{id}/accounts` | Get client's accounts |
| POST | `/api/transfers` | Execute a transfer |
| GET | `/api/statements` | Get account statements |

All frontend requests go through the BFF -- services are never called directly.

## Running in dev mode

```bash
./mvnw quarkus:dev -pl backend
```

Requires the three domain services to be running (or use `bank-demo-ctl up local-manual`).

## Further reading

- [Architecture Overview](../docs/architecture/overview.md)
