# Architecture Overview

## System Architecture

Multi-layered bank demo application demonstrating enterprise patterns:
service layer (above database) -> backend layer (use-case orchestration) -> Angular frontend.

```
+------------------------------------------------------------------+
|                        ANGULAR FRONTEND                           |
|                   (only sees the backend)                         |
+-------------------------------+----------------------------------+
                                | HTTP (OpenAPI-generated client)
                                v
+------------------------------------------------------------------+
|                      BACKEND (BFF layer)                          |
|              dev.benno.backend -- Quarkus :8080                   |
|                                                                   |
|  Responsibility: use-case orchestration, calling multiple         |
|  services, assembling responses for the frontend.                 |
|  Does NOT contain business logic, does NOT write DB directly.     |
|  Calls services through OpenAPI-generated REST clients.           |
+--------+------------------------+--------------------------------+
         |                        |
         | HTTP (OpenAPI           | HTTP (OpenAPI
         | generated client)       | generated client)
         v                        v
+-----------------+  +-----------------+  +--------------------+
| CUSTOMER SVC    |  | ACCOUNT SVC     |  | TRANSACTION SVC    |
| dev.benno.svc   |  | dev.benno.svc   |  | dev.benno.svc      |
| .customer :8081 |  | .account :8082  |  | .transaction :8083 |
|                 |  |                 |  |                    |
| Own DB schema   |  | Own DB schema   |  | Own DB schema      |
| Own Liquibase   |  | Own Liquibase   |  | Own Liquibase      |
| Own OpenAPI     |  | Own OpenAPI     |  | Own OpenAPI        |
| spec            |  | spec            |  | spec               |
+--------+--------+  +--------+--------+  +---------+----------+
         |                     |                     |
         v                     v                     v
+------------------------------------------------------------------+
|                     POSTGRESQL (1 instance)                       |
|         3 separate schemas: customer_svc, account_svc, tx_svc    |
|                        Port: 5432                                 |
+------------------------------------------------------------------+
```

---

## Service Responsibilities and API Endpoints

### Customer Service (port 8081)

**Responsibility:** Customer data management. No other service writes customer data.

**Database tables (`customer_svc` schema):**
- `customer`: id, tax_id (unique tax identifier), first_name, last_name, email, phone, status (ACTIVE/SUSPENDED/CLOSED), created_at

**OpenAPI spec:** `customer-service-api.yaml`

| Endpoint | Method | Description |
|---|---|---|
| /api/customers | GET | List customers |
| /api/customers | POST | Register new customer |
| /api/customers/{id} | GET | Get customer by ID |
| /api/customers/{id} | PUT | Update customer data |
| /api/customers/{id}/status | PUT | Change customer status (ACTIVE/SUSPENDED/CLOSED) |

### Account Service (port 8082)

**Responsibility:** Bank account management, balance queries and modifications. Stores customerId as a reference but does NOT validate it -- that is the backend's job.

**Database tables (`account_svc` schema):**
- `account`: id, account_number (unique, IBAN format), customer_id (long, reference -- NOT a FK!), account_type (CHECKING/SAVINGS), balance, currency (HUF/EUR/USD), status (ACTIVE/FROZEN/CLOSED), created_at
- `balance_history`: id, account_id (FK), old_balance, new_balance, change_amount, reason, created_at

**OpenAPI spec:** `account-service-api.yaml`

| Endpoint | Method | Description |
|---|---|---|
| /api/accounts | GET | List accounts (filter: ?customerId=) |
| /api/accounts | POST | Open new account |
| /api/accounts/{id} | GET | Get account by ID |
| /api/accounts/{id}/status | PUT | Change account status |
| /api/accounts/{id}/balance | GET | Get balance |
| /api/accounts/{id}/balance/credit | POST | Credit (increase balance) |
| /api/accounts/{id}/balance/debit | POST | Debit (decrease balance) |
| /api/accounts/{id}/balance-history | GET | Balance change history |

**Important:** The credit/debit endpoints are atomic operations. The service has no concept of "transfers" -- it only knows that a balance increases or decreases on an account. Transfer logic is handled by the transaction service.

### Transaction Service (port 8083)

**Responsibility:** Transaction recording and state management. Calls Account Service credit/debit endpoints.

**Database tables (`tx_svc` schema):**
- `transaction`: id, transaction_ref (unique UUID), from_account_id, to_account_id, amount, currency, status (PENDING/COMPLETED/FAILED/REVERSED), failure_reason (nullable), created_at, completed_at

**OpenAPI spec:** `transaction-service-api.yaml`

| Endpoint | Method | Description |
|---|---|---|
| /api/transactions | GET | List transactions (filter: ?accountId=) |
| /api/transactions | POST | Initiate new transaction (transfer) |
| /api/transactions/{id} | GET | Get transaction by ID |
| /api/transactions/ref/{ref}/status | GET | Get transaction status by reference |

**Internal logic of POST /api/transactions:**
1. Creates the transaction with PENDING status
2. Calls Account Service debit on the sender's account
3. If successful: calls Account Service credit on the receiver's account
4. If both succeed: sets status to COMPLETED
5. If either fails: FAILED status + compensation (if the debit already occurred, reverses it with a credit)

**Important:** The Transaction Service generates an OpenAPI client from the Account Service spec and calls credit/debit endpoints through it. This is the "OpenAPI client wiring" pattern.

### Backend (BFF -- Backend For Frontend) (port 8080)

**Responsibility:** Use-case orchestration. Calls services and assembles responses for the frontend. Does NOT have its own database.

**OpenAPI spec:** `backend-api.yaml` (the frontend generates a TypeScript client from this)

| Endpoint | Method | Description | Called Services |
|---|---|---|---|
| /api/clients | GET | List customers | Customer |
| /api/clients | POST | Register new customer + open account | Customer + Account |
| /api/clients/{id} | GET | Customer summary (data + accounts) | Customer + Account |
| /api/clients/{id}/accounts | GET | Customer's accounts | Account (customerId filter) |
| /api/clients/{id}/accounts | POST | Open new account for customer | Customer (validation) + Account |
| /api/transfer | POST | Initiate transfer | Customer (validation) + Account (validation) + Transaction |
| /api/transfer/{ref}/status | GET | Transfer status | Transaction |
| /api/accounts/{id}/statement | GET | Account statement (balance + transactions) | Account + Transaction |

**Use-case examples requiring MULTIPLE service calls:**

**1. New customer registration with account opening (POST /api/clients):**
```
Backend:
  1. POST Customer Service /api/customers -> create customer
  2. POST Account Service /api/accounts -> open account (with customerId)
  3. If step 2 fails: compensation? (log + error notification)
  4. Assembles response: customer + account data together
```

**2. Transfer (POST /api/transfer):**
```
Backend:
  1. GET Customer Service /api/customers/{id} -> validate sender customer (ACTIVE?)
  2. GET Account Service /api/accounts/{id} -> validate sender account (ACTIVE? sufficient funds?)
  3. GET Account Service /api/accounts/{id} -> validate receiver account (ACTIVE?)
  4. POST Transaction Service /api/transactions -> initiate transaction
     (Transaction Service internally calls Account Service credit/debit)
  5. Returns the transaction reference to the frontend
```

**3. Account statement (GET /api/accounts/{id}/statement):**
```
Backend:
  1. GET Account Service /api/accounts/{id} -> account data + balance
  2. GET Transaction Service /api/transactions?accountId={id} -> transactions
  3. Assembles the statement: account data + transaction list
```

### Angular Frontend

**Responsibility:** UI, exclusively calls the Backend API (has no knowledge of microservices).

**Pages:**
- Dashboard -- summary (customer count, total balances)
- Client list -- CRUD
- Client detail -- data + accounts + transactions
- Transfer form -- account selection + amount + target account
- Account statement -- balance + transaction history

**TypeScript client generation:** `backend-api.yaml` -> `openapi-generator-cli` -> Angular services.

---

## OpenAPI Code Generation Matrix

This is the core of the system -- who generates what from which spec:

| Project | Generates from spec | Generation type | Purpose |
|---|---|---|---|
| customer-service | customer-service-api.yaml | **server** (jaxrs-spec, interfaceOnly) | REST endpoint interfaces |
| account-service | account-service-api.yaml | **server** (jaxrs-spec, interfaceOnly) | REST endpoint interfaces |
| transaction-service | transaction-service-api.yaml | **server** (jaxrs-spec, interfaceOnly) | REST endpoint interfaces |
| transaction-service | account-service-api.yaml | **client** (java, library=microprofile) | Calling Account Service |
| backend | customer-service-api.yaml | **client** (java, library=microprofile) | Calling Customer Service |
| backend | account-service-api.yaml | **client** (java, library=microprofile) | Calling Account Service |
| backend | transaction-service-api.yaml | **client** (java, library=microprofile) | Calling Transaction Service |
| backend | backend-api.yaml | **server** (jaxrs-spec, interfaceOnly) | Own REST endpoints |
| frontend | backend-api.yaml | **client** (typescript-angular) | Calling Backend from TypeScript |

**Client generation in Quarkus:**
- `generatorName=java` + `library=microprofile` -> generates MicroProfile REST Client interfaces
- In Quarkus, used with the `@RegisterRestClient` + `@Inject @RestClient` pattern
- URL is read from `application.properties` (different per profile)

### Server-side generation (Maven plugin)

Every Java module's `pom.xml` contains:

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <configuration>
        <inputSpec>${project.basedir}/../openapi-specs/backend-api.yaml</inputSpec>
        <generatorName>jaxrs-spec</generatorName>
        <configOptions>
            <interfaceOnly>true</interfaceOnly>       <!-- Interface only, no implementation -->
            <returnResponse>true</returnResponse>     <!-- Response object, not POJO -->
            <useJakartaEe>true</useJakartaEe>        <!-- jakarta.* imports (not javax.*) -->
            <useTags>true</useTags>                  <!-- Separate interface per tag -->
            <useSwaggerAnnotations>false</useSwaggerAnnotations>
        </configOptions>
    </configuration>
</plugin>
```

This generates:
- **Interfaces**: `ClientApi`, `AccountApi`, `TransferApi`, `StatementApi` -- JAX-RS annotated interfaces
- **Model DTOs**: `ClientSummaryDto`, `AccountInfoDto`, `TransferRequest`, etc.

Resource classes **implement** these interfaces:

```java
@ApplicationScoped
public class ClientResource implements ClientApi {
    @Override
    public Response listClients() { ... }

    @Override
    public Response getClientDetail(Long id) { ... }
}
```

### Why hand-written REST clients instead of generated ones?

The OpenAPI Generator's MicroProfile template generates `javax.ws.rs` imports, which are not compatible with Quarkus 3.x (Jakarta EE 10+, `jakarta.ws.rs`). Therefore, **hand-written MicroProfile REST Client interfaces** are used:

```java
@RegisterRestClient(configKey = "account-service-api")
@RegisterClientHeaders(CorrelationIdClientFilter.class)
@Path("/api/accounts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AccountServiceClient {
    @GET
    List<AccountDto> listAccounts(@QueryParam("customerId") Long customerId);

    @POST
    @Path("/{id}/balance/credit")
    BalanceDto creditBalance(@PathParam("id") Long id, BalanceOperationRequest request);
}
```

---

## Project Directory Structure

```
quarkus_demo/
├── pom.xml                          # Parent POM (shared versions, plugins)
├── docs/
│   ├── architecture/
│   │   ├── overview.md              # This document
│   │   ├── frontend.md              # Frontend architecture
│   │   └── future-patterns.md       # Planned enterprise patterns
│   └── guides/                      # Running, setup, deployment guides
│
├── openapi-specs/                   # All OpenAPI specs in one place (SSOT)
│   ├── customer-service-api.yaml
│   ├── account-service-api.yaml
│   ├── transaction-service-api.yaml
│   └── backend-api.yaml
│
├── services/                        # Microservice modules
│   ├── customer-service/            # Quarkus module
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/dev/benno/svc/customer/
│   │       │   │   ├── entity/          # Customer entity
│   │       │   │   └── resource/        # CustomerResource implements CustomerApi
│   │       │   └── resources/
│   │       │       ├── application.properties
│   │       │       ├── application-dev.properties
│   │       │       ├── application-container.properties
│   │       │       └── db/              # Liquibase changelogs
│   │       └── test/
│   │
│   ├── account-service/             # Quarkus module
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/dev/benno/svc/account/
│   │       │   │   ├── entity/          # Account, BalanceHistory entities
│   │       │   │   └── resource/        # AccountResource implements AccountApi
│   │       │   └── resources/
│   │       │       ├── application.properties
│   │       │       ├── application-dev.properties
│   │       │       ├── application-container.properties
│   │       │       └── db/
│   │       └── test/
│   │
│   └── transaction-service/         # Quarkus module
│       ├── pom.xml                  # + OpenAPI client generation from account-service-api.yaml!
│       └── src/
│           ├── main/
│           │   ├── java/dev/benno/svc/transaction/
│           │   │   ├── entity/          # Transaction entity
│           │   │   ├── client/          # Generated REST client configs
│           │   │   └── resource/        # TransactionResource implements TransactionApi
│           │   └── resources/
│           │       ├── application.properties
│           │       ├── application-dev.properties
│           │       ├── application-container.properties
│           │       └── db/
│           └── test/
│
├── backend/                         # Quarkus module (BFF)
│   ├── pom.xml                      # + OpenAPI client generation from ALL THREE service specs!
│   └── src/
│       ├── main/
│       │   ├── java/dev/benno/backend/
│       │   │   ├── client/          # Generated REST client configs
│       │   │   └── resource/        # ClientResource, TransferResource, StatementResource
│       │   └── resources/
│       │       ├── application.properties
│       │       ├── application-dev.properties
│       │       └── application-container.properties
│       └── test/
│
├── frontend/                        # Angular project
│   ├── package.json
│   ├── angular.json
│   ├── Dockerfile.node              # Stage 1: Node.js build
│   ├── Dockerfile.nginx             # Stage 2: nginx serving
│   └── src/
│       ├── app/
│       │   ├── generated/           # OpenAPI-generated TypeScript client (from backend-api.yaml)
│       │   ├── pages/               # Dashboard, ClientList, ClientDetail, Transfer, Statement
│       │   └── shared/              # Shared components
│       └── environments/
│
├── bank-demo-ctl                    # CLI helper scripts
│
├── deployment/
│   ├── local-manual/
│   │   ├── docker-compose.yaml      # Dev mode: PostgreSQL + pgAdmin only
│   │   └── init-schemas.sql         # Creates 3 DB schemas
│   │
│   └── local-docker/
│       ├── docker-compose.yaml      # Full containerized run
│       ├── .env                     # Docker compose env vars (NOT committed)
│       ├── .env.example             # Template for .env
│       ├── secrets/                 # Docker secrets folder (NOT committed)
│       │   ├── db_password.txt
│       │   ├── customer_svc_db_password.txt
│       │   ├── account_svc_db_password.txt
│       │   └── tx_svc_db_password.txt
│       └── secrets.example/         # Template files (committed)
│           └── *.txt
│
├── .gitignore
└── README.md
```

---

## Communication Patterns

### Frontend -> BFF -> Services -> DB

The strict communication flow is:

1. **Frontend -> BFF only:** The Angular frontend calls exclusively the Backend (BFF) API at `/api/*`. It has no knowledge of the existence of microservices. The nginx proxy (in container mode) or Angular CLI proxy (in dev mode) forwards `/api/*` requests to the BFF.

2. **BFF -> Services:** The BFF calls the three microservices through OpenAPI-generated MicroProfile REST Client interfaces. It orchestrates multi-service use cases and assembles responses for the frontend.

3. **Service -> Service (limited):** Only the Transaction Service calls the Account Service (for credit/debit operations during transfers). No other cross-service calls exist.

4. **Services -> DB:** Each service has its own PostgreSQL schema. Services cannot access each other's data directly -- only through REST APIs.

### BFF Orchestration Pattern

The BFF (Backend For Frontend) is a dedicated backend layer tailored to the frontend's needs. It is not a general-purpose API gateway -- it specifically serves the given frontend's use cases.

**Why use a BFF?**
1. **Orchestration**: A single frontend operation (e.g., "customer registration") requires multiple microservice calls (Customer Service + Account Service). The BFF coordinates them.
2. **Aggregation**: The client needs composite views (e.g., customer + their accounts) that require data from multiple services.
3. **Transformation**: The microservices' internal DTOs are not necessarily suitable for direct frontend use. The BFF returns its own DTOs.
4. **Security**: Microservices are not directly accessible from outside -- only through the BFF.
5. **Frontend simplification**: The frontend doesn't need to know about the microservices' existence.

**Example -- customer registration (`ClientResource.registerClient()`):**

```java
@Override
public Response registerClient(RegisterClientRequest request) {
    // 1. Create customer in Customer Service
    CustomerDto customer;
    try {
        customer = customerService.createCustomer(
                new CustomerServiceClient.CreateCustomerRequest(
                        request.getTaxId(),
                        request.getFirstName(),
                        request.getLastName(),
                        request.getEmail(),
                        request.getPhone()));
    } catch (WebApplicationException e) {
        if (e.getResponse().getStatus() == 409) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(errorResponse("Customer already exists with taxId: " + request.getTaxId()))
                    .build();
        }
        throw e;
    }

    // 2. Open account in Account Service
    AccountDto account = accountService.createAccount(
            new AccountServiceClient.CreateAccountRequest(
                    request.getAccountNumber(),
                    customer.id,
                    request.getAccountType().value(),
                    request.getCurrency().value(),
                    0.0));

    return Response.status(Response.Status.CREATED)
            .entity(toDetailDto(customer, List.of(account)))
            .build();
}
```

The frontend sees a single `POST /api/clients` call -- it doesn't know that two separate services are working behind the scenes.

---

## Quarkus Profiles

### Profile-based Configuration

Every service has three property files:

**application.properties** -- shared, profile-independent settings:
```properties
# App info
quarkus.application.name=customer-service

# Hibernate
quarkus.hibernate-orm.schema-management.strategy=none

# Liquibase
quarkus.liquibase.migrate-at-start=true
quarkus.liquibase.change-log=db/changeLog.xml
quarkus.liquibase.default-schema-name=customer_svc
```

**application-dev.properties** -- local development:
```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=demo
quarkus.datasource.password=demo
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/quarkus_demo

# Service URLs (locally everything runs on localhost, different ports)
quarkus.rest-client.account-service-api.url=http://localhost:8082
```

**application-container.properties** -- containerized run:
```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${DB_USER}
quarkus.datasource.password=${DB_PASSWORD}
quarkus.datasource.jdbc.url=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}

# Service URLs (on container network, services are reachable by service name)
quarkus.rest-client.account-service-api.url=http://account-service:8082
```

### Profile Startup

```bash
# Dev mode (default)
quarkus dev

# In container (from environment variable)
java -Dquarkus.profile=container -jar quarkus-run.jar
```

---

## Implemented Enterprise Patterns

### 1. OpenAPI Contract-First

**What is it?** The API specification (YAML) is written first, and code is generated from it -- not the other way around.

**How we apply it:** Four OpenAPI YAML files in the `openapi-specs/` directory, from which the Maven plugin generates server interfaces, and the openapi-generator-cli generates the frontend client.

### 2. BFF Orchestration

**What is it?** The BFF coordinates microservice calls and provides a unified API for the frontend.

**How we apply it:** The `ClientResource` calls Customer Service and Account Service, and returns a unified `ClientDetailDto`.

### 3. MicroProfile REST Client

**What is it?** Declarative, interface-based HTTP client -- no manual `HttpClient` code.

**How we apply it:** Every service call goes through a `@RegisterRestClient` annotated interface. The Quarkus runtime automatically creates the implementation.

```java
@RegisterRestClient(configKey = "account-service-api")
@RegisterClientHeaders(CorrelationIdClientFilter.class)
@Path("/api/accounts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AccountServiceClient {
    @GET
    List<AccountDto> listAccounts(@QueryParam("customerId") Long customerId);

    @POST
    @Path("/{id}/balance/credit")
    BalanceDto creditBalance(@PathParam("id") Long id, BalanceOperationRequest request);
}
```

URL configuration comes from `application.properties`:
```properties
# Dev profile
quarkus.rest-client.account-service-api.url=http://localhost:8082
# Container profile
%container.quarkus.rest-client.account-service-api.url=http://account-service:8082
```

### 4. Compensation (Saga) Pattern

**What is it?** Distributed transaction handling with compensating operations. If a step fails, the previous steps must be rolled back.

**How we apply it:** The `TransactionResource.createTransaction()` method:
1. Debits the sender's account
2. If the credit to the receiver's account fails -> compensation: credit back to the sender's account

```java
} catch (WebApplicationException e) {
    // COMPENSATION: the debit already happened, must reverse it!
    try {
        accountService.creditAccount(tx.fromAccountId,
                new BalanceOperationRequest(request.getAmount(),
                        "Compensation for failed transfer " + tx.transactionRef));
    } catch (Exception compEx) {
        LOG.errorf(compEx, "CRITICAL: Compensation failed!");
    }
    tx.status = TransactionStatusEnum.FAILED;
}
```

### 5. Health Checks (SmallRye Health)

**What is it?** Standardized endpoint (`/q/health`) for querying service health status.

**How we apply it:** The `quarkus-smallrye-health` dependency automatically provides liveness and readiness endpoints. Kubernetes/OpenShift uses these to determine whether a Pod is healthy.

### 6. Correlation ID Propagation

**What is it?** A unique identifier that follows the request across all services -- makes debugging and log analysis easier.

**How we apply it:** Two filters:

`CorrelationIdFilter` (server-side):
```java
@Provider
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext requestContext) {
        String correlationId = requestContext.getHeaderString("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);
    }
}
```

`CorrelationIdClientFilter` (client-side -- for outgoing calls):
```java
@ApplicationScoped
public class CorrelationIdClientFilter implements ClientHeadersFactory {
    @Override
    public MultivaluedMap<String, String> update(...) {
        Object correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            result.putSingle("X-Correlation-ID", correlationId.toString());
        }
        return result;
    }
}
```

The flow: Frontend -> BFF (generates correlationId) -> Customer Service (receives it) -> visible in logs.

### 7. Profile-based Configuration

**What is it?** Different settings for dev and container environments.

**How we apply it:**
```properties
# Dev profile (local development)
quarkus.rest-client.account-service-api.url=http://localhost:8082

# Container profile (OpenShift)
%container.quarkus.rest-client.account-service-api.url=http://account-service:8082
```

### 8. Database-per-Service

**What is it?** Each microservice uses its own independent database schema.

**How we apply it:** Separate PostgreSQL schemas: `customer_svc`, `account_svc`, `transaction_svc`. Services cannot access each other's data directly -- only through APIs.

---

## Logging and Correlation ID Strategy

### Service Layer Logging

Every service uses structured JSON logging (`quarkus-logging-json` extension):

```properties
# application.properties
quarkus.log.console.json=false               # Dev: human-readable text
# application-container.properties
quarkus.log.console.json=true                # Container: JSON (for log aggregators)

quarkus.log.category."dev.benno".level=DEBUG
quarkus.log.console.format=%d{HH:mm:ss} %-5p [%c{2.}] (%t) %s%e%n
```

**What we log in services:**
- Every incoming request (request path, method, parameters)
- Business decisions (e.g., "Insufficient funds for debit: accountId=42, requested=50000, available=30000")
- DB operation results
- Errors with full stack traces

### Backend Layer Logging

**What we log in the backend:**
- Incoming frontend requests
- Every service call outcome (which service was called, duration, success/failure)
- Orchestration decisions (e.g., "Transfer validation: customer ACTIVE, from account ACTIVE, to account ACTIVE, sufficient funds")
- Compensation steps (e.g., "Debit succeeded but credit failed, initiating compensation")

### Correlation ID

Every request gets a unique `X-Correlation-ID` header:
- The frontend does not send one -> the backend generates one
- The backend passes it along in every service call
- The services log it
- This way, all service log entries for a single frontend request can be linked

Implementation: Quarkus `ContainerRequestFilter` + `ClientRequestFilter`.

---

## Quarkus Framework Components

### CDI (Contexts and Dependency Injection)

Quarkus uses CDI for dependency injection. The `@ApplicationScoped` annotation marks a class as a singleton (single instance).

```java
@ApplicationScoped
public class ClientResource implements ClientApi {
    @RestClient
    CustomerServiceClient customerService;  // CDI injects

    @RestClient
    AccountServiceClient accountService;    // CDI injects
}
```

### JAX-RS (REST Endpoints)

The generated interfaces use JAX-RS annotations (`@GET`, `@POST`, `@Path`, etc.). Quarkus registers these automatically.

### Hibernate ORM Panache

Panache is a simplified version of Hibernate ORM. It follows the Active Record pattern:

```java
// Listing
List<Account> accounts = Account.list("customerId", customerId);

// Finding by ID
Account account = Account.findById(id);

// Persisting
account.persist();

// Counting
long count = Account.count("accountNumber", accountNumber);
```

The `PanacheEntityBase` base class provides these static methods. Entities use public fields (not private + getter/setter), resulting in simpler code.

### Liquibase Migrations

Database schema changes are managed by Liquibase. Every service has its own `changeLog.xml` that creates tables and initial data.

Example: `account-service` creates the `account_svc.account` and `account_svc.balance_history` tables.

---

## Deployment Modes

### Comparison Table

| Aspect | Dev Mode | Container Mode |
|---|---|---|
| **Infrastructure** | docker-compose: PostgreSQL + pgAdmin only | docker-compose: all services containerized |
| **Services** | Run from CLI with `quarkus dev` | Built as Docker images, run as containers |
| **Frontend** | `ng serve` (webpack dev server) | nginx container serving static files |
| **DB access** | Direct: localhost:5432, user=demo | Via Docker network, secrets for passwords |
| **Quarkus profile** | `dev` (default) | `container` |
| **Service URLs** | `http://localhost:808x` | `http://service-name:808x` |
| **Logging** | Human-readable text | JSON (for log aggregators) |
| **Hot reload** | Yes (Quarkus dev mode + ng serve) | No (requires rebuild) |
| **Terminals needed** | 6 (postgres + 4 services + frontend) | 1 (docker compose up) |

### Dev Mode (CLI-based)

```
deployment/local-manual/docker-compose.yaml -> PostgreSQL + pgAdmin
6 terminal windows:
  1. docker compose -f deployment/local-manual/docker-compose.yaml up
  2. cd services/customer-service && quarkus dev
  3. cd services/account-service && quarkus dev
  4. cd services/transaction-service && quarkus dev
  5. cd backend && quarkus dev
  6. cd frontend && ng serve
```

**deployment/local-manual/docker-compose.yaml:**
```yaml
services:
  postgres:
    image: postgres:17
    container_name: bank_demo_db
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: quarkus_demo
      POSTGRES_USER: demo
      POSTGRES_PASSWORD: demo
    volumes:
      - ./init-schemas.sql:/docker-entrypoint-initdb.d/init.sql
      - pgdata:/var/lib/postgresql/data

  pgadmin:
    image: dpage/pgadmin4
    container_name: bank_demo_pgadmin
    ports:
      - "5050:80"
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@demo.dev
      PGADMIN_DEFAULT_PASSWORD: admin

volumes:
  pgdata:
```

**deployment/local-manual/init-schemas.sql:**
```sql
CREATE SCHEMA IF NOT EXISTS customer_svc;
CREATE SCHEMA IF NOT EXISTS account_svc;
CREATE SCHEMA IF NOT EXISTS tx_svc;
```

### Container Mode (docker-compose, production simulation)

**deployment/local-docker/docker-compose.yaml:**
```yaml
services:
  postgres:
    image: postgres:17
    secrets:
      - db_password
    environment:
      POSTGRES_DB: quarkus_demo
      POSTGRES_USER: bankadmin
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
    volumes:
      - ../local-manual/init-schemas.sql:/docker-entrypoint-initdb.d/init.sql
      - pgdata_prod:/var/lib/postgresql/data
    networks:
      - bank-net

  customer-service:
    build:
      context: ../../services/customer-service
      dockerfile: src/main/docker/Dockerfile.jvm
    environment:
      QUARKUS_PROFILE: container
      DB_HOST: postgres
      DB_NAME: quarkus_demo
      DB_USER: bankadmin
      DB_PASSWORD_FILE: /run/secrets/customer_svc_db_password
    secrets:
      - customer_svc_db_password
    depends_on:
      - postgres
    networks:
      - bank-net

  account-service:
    build:
      context: ../../services/account-service
      dockerfile: src/main/docker/Dockerfile.jvm
    environment:
      QUARKUS_PROFILE: container
      DB_HOST: postgres
      DB_NAME: quarkus_demo
      DB_USER: bankadmin
      DB_PASSWORD_FILE: /run/secrets/account_svc_db_password
    secrets:
      - account_svc_db_password
    depends_on:
      - postgres
    networks:
      - bank-net

  transaction-service:
    build:
      context: ../../services/transaction-service
      dockerfile: src/main/docker/Dockerfile.jvm
    environment:
      QUARKUS_PROFILE: container
      DB_HOST: postgres
      DB_NAME: quarkus_demo
      DB_USER: bankadmin
      DB_PASSWORD_FILE: /run/secrets/tx_svc_db_password
      ACCOUNT_SERVICE_URL: http://account-service:8082
    secrets:
      - tx_svc_db_password
    depends_on:
      - account-service
    networks:
      - bank-net

  backend:
    build:
      context: ../../backend
      dockerfile: src/main/docker/Dockerfile.jvm
    ports:
      - "8080:8080"
    environment:
      QUARKUS_PROFILE: container
      CUSTOMER_SERVICE_URL: http://customer-service:8081
      ACCOUNT_SERVICE_URL: http://account-service:8082
      TRANSACTION_SERVICE_URL: http://transaction-service:8083
    depends_on:
      - customer-service
      - account-service
      - transaction-service
    networks:
      - bank-net

  frontend:
    build:
      context: ../../frontend
    ports:
      - "4200:80"
    depends_on:
      - backend
    networks:
      - bank-net

secrets:
  db_password:
    file: ./secrets/db_password.txt
  customer_svc_db_password:
    file: ./secrets/customer_svc_db_password.txt
  account_svc_db_password:
    file: ./secrets/account_svc_db_password.txt
  tx_svc_db_password:
    file: ./secrets/tx_svc_db_password.txt

volumes:
  pgdata_prod:

networks:
  bank-net:
    driver: bridge
```

---

## Technology Stack Summary

| Component | Technology | Version |
|---|---|---|
| Language | Java | 21 LTS |
| Backend framework | Quarkus | 3.34.x |
| ORM | Hibernate ORM Panache | (Quarkus BOM) |
| DB | PostgreSQL | 17 |
| DB migration | Liquibase | (Quarkus BOM) |
| REST | Quarkus REST (RESTEasy Reactive) | (Quarkus BOM) |
| REST client | Quarkus REST Client Reactive | (Quarkus BOM) |
| OpenAPI server generation | openapi-generator-maven-plugin (jaxrs-spec) | 7.12.0 |
| OpenAPI client generation (Java) | openapi-generator-maven-plugin (java/microprofile) | 7.12.0 |
| OpenAPI client generation (TS) | @openapitools/openapi-generator-cli (typescript-angular) | 7.12.0 |
| API documentation | SmallRye OpenAPI + Swagger UI | (Quarkus BOM) |
| Health check | SmallRye Health | (Quarkus BOM) |
| Frontend | Angular | 19.x |
| Frontend CSS | Angular Material | latest |
| Build | Maven (multi-module) | 3.9.x |
| Container | Docker / Podman | latest |
| Orchestration (dev) | docker-compose | latest |
| Orchestration (prod sim) | OpenShift (CRC) or docker-compose | latest |

## Quarkus Extensions per Service

**Every service:**
- `quarkus-rest-jackson` -- REST + JSON
- `quarkus-hibernate-orm-panache` -- ORM
- `quarkus-jdbc-postgresql` -- Postgres driver
- `quarkus-liquibase` -- DB migration
- `quarkus-smallrye-openapi` -- OpenAPI spec generation + Swagger UI
- `quarkus-smallrye-health` -- Health check (/q/health)
- `quarkus-container-image-docker` -- Docker image build
- `quarkus-logging-json` -- JSON logging (in container mode)

**Transaction Service additionally:**
- `quarkus-rest-client-jackson` -- for calling Account Service

**Backend additionally:**
- `quarkus-rest-client-jackson` -- for calling all three services

---

## Further Reading

- [Frontend Architecture](frontend.md)
- [Future Patterns and Roadmap](future-patterns.md)
