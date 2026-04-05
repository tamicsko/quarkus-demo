# Future Patterns and Implementation Roadmap

This document collects all enterprise patterns that are not yet implemented in the project, along with descriptions, rationale, and code examples showing how they could be implemented. These come from the backend (14 patterns) and frontend (9 patterns) architecture reviews.

---

## Backend: Missing Enterprise Patterns

### 1. Domain Layer

**What is it?** Clean domain objects that contain business logic, without JPA annotations or framework dependencies.

**Why is it important?** Currently, the `Account` entity is both a JPA entity AND a domain object. This ties the business logic to the database layer. If we were to switch databases (e.g., to MongoDB), the entire domain logic would need to be rewritten.

**How to implement:**

```java
// Domain object (no JPA, no framework)
public class Account {
    private final AccountId id;
    private final AccountNumber number;
    private final Money balance;
    private AccountStatus status;

    public void credit(Money amount) {
        if (this.status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(this.id);
        }
        this.balance = this.balance.add(amount);
    }

    public void debit(Money amount) {
        if (this.balance.isLessThan(amount)) {
            throw new InsufficientFundsException(this.id, amount, this.balance);
        }
        this.balance = this.balance.subtract(amount);
    }
}

// JPA entity (separate, only in the persistence layer)
@Entity
@Table(name = "account", schema = "account_svc")
public class AccountJpaEntity extends PanacheEntityBase {
    // ... JPA fields ...

    public Account toDomain() { ... }
    public static AccountJpaEntity fromDomain(Account domain) { ... }
}
```

### 2. Use Cases / Application Services

**What is it?** A separate class that implements a specific business operation (use case). Separates the REST layer from the business logic.

**Why is it important?** Currently, the `ClientResource` class handles HTTP requests AND orchestration logic simultaneously. This violates the Single Responsibility Principle and makes testing harder.

**How to implement:**

```java
// Use case class
@ApplicationScoped
public class RegisterClientUseCase {
    @Inject CustomerRepository customerRepo;
    @Inject AccountRepository accountRepo;

    public ClientDetail execute(RegisterClientCommand command) {
        // Business logic goes here
        Customer customer = Customer.create(command.taxId(), command.name(), command.email());
        customerRepo.save(customer);

        Account account = Account.openNew(customer.id(), command.accountNumber(), command.currency());
        accountRepo.save(account);

        return new ClientDetail(customer, List.of(account));
    }
}

// Resource class (HTTP layer only)
@ApplicationScoped
public class ClientResource implements ClientApi {
    @Inject RegisterClientUseCase registerClient;

    @Override
    public Response registerClient(RegisterClientRequest request) {
        var result = registerClient.execute(toCommand(request));
        return Response.status(201).entity(toDto(result)).build();
    }
}
```

### 3. Ports and Adapters (Hexagonal Architecture)

**What is it?** The business logic (domain) is at the center. Incoming calls are received by "ports" (interfaces), outgoing calls are implemented by "adapters".

**Why is it important?** This way the business logic is independent of technological details (REST, JPA, message broker, etc.).

**How to implement:**

```
account-service/
  domain/
    model/
      Account.java           # Clean domain object
      Money.java              # Value object
    port/
      in/
        CreditAccountPort.java    # Incoming port (interface)
        DebitAccountPort.java
      out/
        AccountRepository.java    # Outgoing port (interface)
        EventPublisher.java       # Outgoing port (interface)
  application/
    service/
      CreditAccountService.java  # Use case implementation
  adapter/
    in/
      rest/
        AccountRestAdapter.java   # REST controller (incoming adapter)
    out/
      persistence/
        AccountJpaAdapter.java    # JPA implementation (outgoing adapter)
        AccountJpaEntity.java
      messaging/
        KafkaEventAdapter.java    # Kafka (outgoing adapter)
```

### 4. Repository Pattern

**What is it?** An abstract interface for persistence operations that hides the concrete database implementation.

**Why is it important?** Currently, we call `Account.findById()` Panache methods directly in the Resource class. This tightly couples the REST layer to the JPA implementation.

**How to implement:**

```java
// Port (interface in the domain layer)
public interface AccountRepository {
    Optional<Account> findById(AccountId id);
    void save(Account account);
    List<Account> findByCustomerId(CustomerId customerId);
}

// Adapter (JPA implementation)
@ApplicationScoped
public class AccountJpaRepository implements AccountRepository {
    @Override
    public Optional<Account> findById(AccountId id) {
        AccountJpaEntity entity = AccountJpaEntity.findById(id.value());
        return Optional.ofNullable(entity).map(AccountJpaEntity::toDomain);
    }
}
```

### 5. Domain Events (Event-Driven Communication)

**What is it?** Services don't call each other directly; instead, they publish events that other services subscribe to.

**Why is it important?** Currently, the Transaction Service calls the Account Service with a synchronous REST call. If the Account Service is unavailable, the transaction immediately fails. With domain events, asynchronous and more reliable communication is possible.

**How to implement:** Apache Kafka or RabbitMQ:

```java
// Transaction Service publishes the event
@Outgoing("transfer-initiated")
public Uni<Message<TransferEvent>> publishTransfer(TransferEvent event) {
    return Uni.createFrom().item(Message.of(event));
}

// Account Service subscribes
@Incoming("transfer-initiated")
public CompletionStage<Void> onTransferInitiated(TransferEvent event) {
    accountService.debit(event.fromAccountId(), event.amount());
    accountService.credit(event.toAccountId(), event.amount());
    // Publish result event...
}
```

### 6. CQRS (Command Query Responsibility Segregation)

**What is it?** Separate models for writing (command) and reading (query). Writes go to a normalized database, reads come from a denormalized, optimized view.

**Why is it important?** The account statement (`StatementResource`) currently makes three separate HTTP calls (Account Service + Transaction Service + Balance History) and joins the data in memory. With CQRS, there would be a pre-prepared view.

**How to implement:**

```
Command side (writes):
  POST /api/accounts/credit  -> Account aggregate update
  POST /api/transactions     -> Transaction aggregate create
                               | Event publishing
Query side (reads):
  Event handler -> Update denormalized view
  GET /api/accounts/{id}/statement -> Direct read from optimized view
```

### 7. API Gateway

**What is it?** A centralized entry point in front of all microservices that provides routing, rate limiting, authentication, and logging.

**Why is it important?** Currently, the nginx proxy in the frontend serves as the API gateway -- but this doesn't scale and doesn't provide enterprise-level functionality.

**How to implement:** Kong, Envoy, or OpenShift's built-in Service Mesh (Istio):

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: bank-demo-routing
spec:
  hosts:
    - bank-demo.example.com
  http:
    - match:
        - uri:
            prefix: /api/clients
      route:
        - destination:
            host: backend
            port:
              number: 8080
```

### 8. Service Mesh (Istio)

**What is it?** An infrastructure layer for inter-microservice communication: mTLS (mandatory encryption), traffic management, observability.

**Why is it important?** Currently, unencrypted HTTP communication runs between services. In a banking environment, this is unacceptable.

**How to implement:** OpenShift Service Mesh (Istio-based):
```yaml
# Automatic sidecar proxy injection
apiVersion: apps/v1
kind: Deployment
metadata:
  annotations:
    sidecar.istio.io/inject: "true"
```

### 9. Distributed Tracing

**What is it?** Tracking a request's path across all services, with a visual timeline diagram.

**Why is it important?** Currently, with the Correlation ID we can see that logs are related, but there is no visual temporal overview and wait times are not visible.

**How to implement:** OpenTelemetry + Jaeger:

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
```

```properties
# application.properties
quarkus.otel.exporter.otlp.traces.endpoint=http://jaeger-collector:4317
```

Quarkus automatically propagates the trace context in REST Client calls.

### 10. Circuit Breaker (MicroProfile Fault Tolerance)

**What is it?** If a service becomes unavailable, the circuit breaker stops the calls (open state) and quickly returns an error. This way we don't overload an already overwhelmed service, and the caller doesn't wait for minutes.

**Why is it important?** Currently, if the Account Service goes down, the BFF's every request waits with a timeout (30-60 sec). The circuit breaker would immediately signal an error and could return a fallback value.

**How to implement:**

```java
@RegisterRestClient(configKey = "account-service-api")
public interface AccountServiceClient {

    @GET
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000)
    @Timeout(2000)
    @Retry(maxRetries = 2)
    @Fallback(fallbackMethod = "getAccountFallback")
    AccountDto getAccount(@PathParam("id") Long id);

    default AccountDto getAccountFallback(Long id) {
        // Cached value or error indication
        throw new ServiceUnavailableException("Account Service temporarily unavailable");
    }
}
```

### 11. Event Sourcing

**What is it?** State changes are stored as events (not the current state). The current state is derived from "replaying" all events.

**Why is it important?** In a banking environment, the transaction history is critical. Event sourcing ensures that information is never lost and the state can be restored to any point in time.

**How to implement:**

```java
// Events
sealed interface AccountEvent {
    record AccountOpened(AccountId id, Money initialBalance) implements AccountEvent {}
    record MoneyCredited(AccountId id, Money amount, String reason) implements AccountEvent {}
    record MoneyDebited(AccountId id, Money amount, String reason) implements AccountEvent {}
}

// Aggregate: current state comes from applying events
public class AccountAggregate {
    private Money balance = Money.ZERO;

    public void apply(AccountEvent event) {
        switch (event) {
            case MoneyCredited e -> this.balance = balance.add(e.amount());
            case MoneyDebited e -> this.balance = balance.subtract(e.amount());
            // ...
        }
    }
}
```

### 12. Authentication / Authorization (Keycloak + JWT)

**What is it?** Identity management and authorization checking for users and services.

**Why is it important?** Currently, there is no authentication at all -- anyone can access all endpoints.

**How to implement:**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-oidc</artifactId>
</dependency>
```

```properties
# application.properties
quarkus.oidc.auth-server-url=https://keycloak.example.com/realms/bank-demo
quarkus.oidc.client-id=backend-service
```

```java
@Path("/api/clients")
@RolesAllowed("bank-employee")
public class ClientResource implements ClientApi {
    @Override
    @RolesAllowed("bank-admin")
    public Response registerClient(RegisterClientRequest request) { ... }
}
```

### 13. Unit and Integration Tests

**What is it?** Automated tests to ensure correct code behavior.

**Why is it important?** Currently, the test dependencies are in the `pom.xml` files (JUnit, REST Assured), but not a single test is implemented. The `-DskipTests` flag in the pipeline also shows that tests are not running.

**How to implement:**

```java
// Unit test (mocked dependencies)
@QuarkusTest
public class ClientResourceTest {
    @InjectMock
    @RestClient
    CustomerServiceClient customerService;

    @InjectMock
    @RestClient
    AccountServiceClient accountService;

    @Test
    public void testRegisterClient() {
        Mockito.when(customerService.createCustomer(any()))
                .thenReturn(new CustomerDto(1L, "12345", "Janos", "Kovacs", ...));

        given()
            .contentType(ContentType.JSON)
            .body(new RegisterClientRequest(...))
        .when()
            .post("/api/clients")
        .then()
            .statusCode(201)
            .body("firstName", equalTo("Janos"));
    }
}

// Integration test (Testcontainers + real DB)
@QuarkusIntegrationTest
public class AccountServiceIT {
    @Test
    public void testCreditAndDebit() {
        // Quarkus automatically starts a PostgreSQL container
        // and the test runs against the real database
    }
}
```

### 14. DTO Mapping Layer (MapStruct)

**What is it?** Automatic mapping between domain objects and DTOs, without boilerplate code.

**Why is it important?** Currently, every Resource class has manual `toDto()` and `toSummaryDto()` methods. This is a lot of repetitive, error-prone code.

**How to implement:**

```java
@Mapper(componentModel = "cdi")
public interface ClientMapper {
    ClientSummaryDto toSummary(CustomerDto customer, int accountCount);
    ClientDetailDto toDetail(CustomerDto customer, List<AccountDto> accounts);
    AccountInfoDto toAccountInfo(AccountDto account);
}

// Usage:
@ApplicationScoped
public class ClientResource implements ClientApi {
    @Inject ClientMapper mapper;

    @Override
    public Response listClients() {
        List<CustomerDto> customers = customerService.listCustomers();
        List<AccountDto> allAccounts = accountService.listAccounts(null);
        List<ClientSummaryDto> result = customers.stream()
                .map(c -> mapper.toSummary(c, countAccounts(c.id, allAccounts)))
                .toList();
        return Response.ok(result).build();
    }
}
```

MapStruct generates the mapping code at compile time -- no reflection, so it's fast and type-safe.

---

## Frontend: Missing Enterprise Patterns

### 1. State Management

**What is it?** Centralized, predictable state management for the entire application.

**Why is it important?** Currently, every component loads its own data (HTTP call in `ngOnInit`). This can result in multiple data downloads (e.g., TransferComponent separately loads all customer data), and state is not synchronized between components.

**How to implement:** Angular Signal Store (new, official solution) or NgRx:

```typescript
// Signal Store example
export const ClientStore = signalStore(
  withState({ clients: [] as ClientSummaryDto[], loading: false }),
  withMethods((store, clientService = inject(ClientService)) => ({
    loadClients() {
      patchState(store, { loading: true });
      clientService.listClients().subscribe(clients => {
        patchState(store, { clients, loading: false });
      });
    }
  }))
);
```

### 2. Error Handling

**What is it?** A global error handling strategy -- unified handling of HTTP errors, network errors, and unexpected exceptions.

**Why is it important?** Currently, errors are handled in an ad-hoc manner: `alert()` in ClientListComponent, `snackBar` in ClientDetailComponent, and no error handling at all in DashboardComponent. This gives an inconsistent user experience.

**How to implement:** HTTP Interceptor + global error handler:

```typescript
// HTTP Error Interceptor
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 0) {
        // Network error
        notificationService.showError('No network connection');
      } else if (error.status === 500) {
        notificationService.showError('Server error occurred');
      }
      return throwError(() => error);
    })
  );
};
```

### 3. Authentication / Authorization

**What is it?** User login and authorization checking.

**Why is it important?** Currently, anyone can access all functions -- there is no login, no role-based access control.

**How to implement:** OAuth2 / OIDC integration with Keycloak:

```typescript
// Angular OIDC Security configuration
provideAuth({
  config: {
    authority: 'https://keycloak.example.com/realms/bank-demo',
    redirectUrl: window.location.origin,
    clientId: 'bank-demo-frontend',
    scope: 'openid profile email',
    responseType: 'code'
  }
})
```

Route guards can protect every page:
```typescript
{ path: 'transfer', loadComponent: ..., canActivate: [authGuard] }
```

### 4. Feature Modules / Domain-Driven Structure

**What is it?** The application is structured by functional areas (domains).

**Why is it important?** Currently, the directory structure is flat (`pages/`). In a larger application, this becomes unmanageable.

**How to implement:**

```
src/app/
  features/
    client/
      components/
        client-list/
        client-detail/
      services/
        client-facade.service.ts
      store/
        client.store.ts
      client.routes.ts
    transfer/
      components/
      services/
      store/
      transfer.routes.ts
    statement/
      ...
  shared/
    components/
      error-display/
      loading-spinner/
    pipes/
    directives/
  core/
    interceptors/
    guards/
    services/
```

### 5. Unit Tests

**What is it?** Automated tests for verifying the correct behavior of individual components and services.

**Why is it important?** Currently, not a single test exists. Refactoring and adding new features is dangerous -- there is no safety net.

**How to implement:** Jest or Jasmine/Karma:

```typescript
describe('DashboardComponent', () => {
  it('should display client count', () => {
    const mockClientService = jasmine.createSpyObj('ClientService', ['listClients']);
    mockClientService.listClients.and.returnValue(of([
      { id: 1, firstName: 'Janos', accountCount: 2 }
    ]));

    const component = new DashboardComponent(mockClientService);
    component.ngOnInit();

    expect(component.clients().length).toBe(1);
    expect(component.totalAccounts()).toBe(2);
  });
});
```

### 6. E2E Tests

**What is it?** End-to-end tests that test the complete application in a browser.

**Why is it important?** Unit tests don't cover the interaction between components, routing, or API integration.

**How to implement:** Playwright or Cypress:

```typescript
// Playwright example
test('should register new client', async ({ page }) => {
  await page.goto('/clients');
  await page.click('text=New customer');
  await page.fill('[placeholder="Tax ID"]', '1234567890');
  await page.fill('[placeholder="Last name"]', 'Kovacs');
  await page.click('text=Register');
  await expect(page.locator('table')).toContainText('Kovacs');
});
```

### 7. Environment-dependent Configuration

**What is it?** Different settings for dev/staging/prod environments.

**Why is it important?** Currently, `environment.ts` only handles two environments (dev and production). In a productive system, staging, feature branches, etc., would be needed.

**How to implement:** External configuration file loaded at container startup:

```typescript
// Runtime configuration loading
export function initializeApp(http: HttpClient) {
  return () => http.get('/assets/config.json').pipe(
    tap(config => ConfigService.setConfig(config))
  );
}
```

### 8. CSP Headers and XSS Protection

**What is it?** Content Security Policy headers that prevent the loading of malicious scripts.

**Why is it important?** In a banking application, security is critically important. XSS attacks can have serious consequences.

**How to implement:** Extending the nginx configuration:

```nginx
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header X-XSS-Protection "1; mode=block" always;
```

### 9. Accessibility (a11y)

**What is it?** Application usability for users with disabilities (screen readers, keyboard navigation).

**Why is it important?** It is a legal requirement (EU Accessibility Act) and provides a better user experience for everyone.

**How to implement:**
- Adding ARIA attributes
- Ensuring keyboard navigation
- Checking color contrast
- Using Angular CDK a11y module
- axe-core or pa11y automatic audits

### 10. Responsive Design / Mobile Support

**What is it?** Application usability across different screen sizes.

**Why is it important?** The current interface works optimally on desktop, but is difficult to use on mobile devices.

**How to implement:** Angular Flex Layout or CSS media queries, Angular CDK BreakpointObserver:

```typescript
constructor(private breakpointObserver: BreakpointObserver) {
  this.isMobile = this.breakpointObserver
    .observe([Breakpoints.HandsetPortrait])
    .pipe(map(result => result.matches));
}
```

---

## Implementation Roadmap

### Phase 1: Project Restructuring to Multi-Module
1. Create Parent POM (shared versions, plugin management)
2. Scaffold customer-service module based on current code
3. Scaffold account-service module
4. Scaffold transaction-service module
5. Scaffold backend module
6. Git init + .gitignore + first commit

### Phase 2: OpenAPI Specs
7. customer-service-api.yaml
8. account-service-api.yaml
9. transaction-service-api.yaml
10. backend-api.yaml

### Phase 3: Service Layer Implementation
11. Customer Service -- entities, Liquibase, generated interface implementation
12. Account Service -- entities, Liquibase, generated interface implementation (with credit/debit logic)
13. Transaction Service -- entities, Liquibase, generated interface + OpenAPI client wiring to Account Service
14. Introduce logging + Correlation ID

### Phase 4: Backend (BFF) Implementation
15. OpenAPI client generation from all three services
16. backend-api.yaml server generation + implementation
17. Use-case orchestration (registration, transfer, statement)
18. Backend logging

### Phase 5: Frontend (Angular)
19. Angular project scaffolding
20. OpenAPI TypeScript client generation from backend-api.yaml
21. Page implementation (Dashboard, Clients, Transfer, Statement)

### Phase 6: Dev Mode Docker-Compose
22. deployment/local-manual/docker-compose.yaml (Postgres + pgAdmin)
23. init-schemas.sql
24. Full system test from CLI

### Phase 7: Containerized Deployment
25. Dockerfiles for every service
26. deployment/local-docker/docker-compose.yaml + secrets
27. application-container.properties in every module
28. Full system test in containers

### Phase 8: OpenShift
29. CRC installation OR Developer Sandbox registration
30. OpenShift manifests (Secret, ConfigMap, Deployment, Service, Route)
31. Deploy + test

---

## Further Reading

- [Architecture Overview](overview.md)
- [Frontend Architecture](frontend.md)
