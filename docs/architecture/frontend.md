# Frontend Architecture

## Technology Stack

| Technology | Version | Purpose |
|---|---|---|
| Angular | 19.x | Frontend framework |
| Angular Material | 19.x | UI components (Material Design) |
| ngx-translate | 16.x | Language support (i18n) |
| RxJS | 7.8.x | Reactive programming |
| TypeScript | 5.7.x | Typed JavaScript |
| openapi-generator-cli | 7.12.0 | API client generation |

---

## Angular 19 Standalone Components

In Angular 19, the earlier NgModule-based architecture is deprecated. Instead, **standalone components** are used, which directly import their dependencies.

### The old pattern (NgModule):
```typescript
// OLD -- we do NOT use this
@NgModule({
  declarations: [AppComponent, DashboardComponent],
  imports: [BrowserModule, RouterModule, MatToolbarModule],
  bootstrap: [AppComponent]
})
export class AppModule {}
```

### The new pattern (standalone):
```typescript
// NEW -- our project uses this pattern
@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatButtonModule, MatIconModule, MatMenuModule,
    TranslateModule,
    UpperCasePipe
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  // ...
}
```

**Advantages:**
- No NgModule needed -- less boilerplate
- The component precisely declares what it uses
- Better tree-shaking (smaller bundle)
- Simpler lazy loading

### Application Configuration (`app.config.ts`)

Instead of modules, `provideXxx()` functions are used:

```typescript
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
    provideAnimationsAsync(),
    provideApi({ basePath: environment.apiBasePath }),  // OpenAPI client
    provideTranslateService({ fallbackLang: 'hu' }),
    provideTranslateHttpLoader()
  ]
};
```

Every "service" is registered as a `provide` function: router, HTTP client, animations, i18n, and the OpenAPI-generated API client.

---

## Signal-based Reactive State

In Angular 19, **Signal** is the recommended state management approach. A Signal is a reactive primitive that automatically notifies the template when its value changes.

### The old pattern (property binding):
```typescript
// OLD
loading = true;
clients: ClientSummaryDto[] = [];
```

### Our pattern (Signal):
```typescript
// NEW -- Signal-based
loading = signal(true);
clients = signal<ClientSummaryDto[]>([]);
totalAccounts = signal(0);
```

Example from `DashboardComponent`:

```typescript
export class DashboardComponent implements OnInit {
  loading = signal(true);
  clients = signal<ClientSummaryDto[]>([]);
  totalAccounts = signal(0);

  constructor(private clientService: ClientService) {}

  ngOnInit() {
    this.clientService.listClients().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        const total = clients.reduce((sum, c) => sum + (c.accountCount ?? 0), 0);
        this.totalAccounts.set(total);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }
}
```

In the template, the Signal is called as a function:

```html
@if (loading()) {
  <mat-spinner diameter="48"></mat-spinner>
} @else {
  <mat-card>
    <mat-card-title>{{ clients().length }}</mat-card-title>
  </mat-card>
}
```

---

## New Control Flow Syntax (@if / @for / @switch)

Angular 19 replaces the older structural directives (`*ngIf`, `*ngFor`, `*ngSwitch`) with a new block-based control flow syntax:

**@if** -- conditional rendering:
```html
@if (loading()) {
  <mat-spinner diameter="48"></mat-spinner>
} @else {
  <div class="content">...</div>
}
```

**@for** -- iteration:
```html
@for (client of clients(); track client.id) {
  <tr>
    <td>{{ client.firstName }}</td>
    <td>{{ client.lastName }}</td>
  </tr>
}
```

**@switch** -- multi-branch selection:
```html
@switch (client.status) {
  @case ('ACTIVE') { <mat-chip color="primary">Active</mat-chip> }
  @case ('SUSPENDED') { <mat-chip color="warn">Suspended</mat-chip> }
  @case ('CLOSED') { <mat-chip>Closed</mat-chip> }
}
```

**Advantages over the old directives:**
- No additional imports needed (built into the template engine)
- More readable, block-based syntax
- Better type narrowing inside blocks
- Mandatory `track` in `@for` prevents performance issues

---

## Lazy Loading Strategy

Every page is **lazy-loaded** -- it only loads when the user navigates to it. This reduces the initial load time.

```typescript
export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard.component')
      .then(m => m.DashboardComponent)
  },
  {
    path: 'clients',
    loadComponent: () => import('./pages/client-list/client-list.component')
      .then(m => m.ClientListComponent)
  },
  {
    path: 'clients/:id',
    loadComponent: () => import('./pages/client-detail/client-detail.component')
      .then(m => m.ClientDetailComponent)
  },
  {
    path: 'transfer',
    loadComponent: () => import('./pages/transfer/transfer.component')
      .then(m => m.TransferComponent)
  },
  {
    path: 'statement/:id',
    loadComponent: () => import('./pages/statement/statement.component')
      .then(m => m.StatementComponent)
  }
];
```

The `loadComponent` uses dynamic imports (`import()`) -- the bundler (esbuild) creates a separate chunk for each page.

---

## OpenAPI Code Generation

### The Contract-First Approach

1. **Spec writing**: `openapi-specs/backend-api.yaml` -- the BFF API description
2. **Client generation**: `openapi-generator-cli` generates the TypeScript Angular client
3. **Usage**: Angular components use the generated services

### The generate command

In `package.json`:

```json
"generate-api": "openapi-generator-cli generate -i ../openapi-specs/backend-api.yaml -g typescript-angular -o src/app/generated --additional-properties=ngVersion=19"
```

This generates the following files:

```
src/app/generated/
  api/
    account.service.ts      # AccountService (deposit, withdraw)
    client.service.ts       # ClientService (list, detail, register)
    statement.service.ts    # StatementService (getStatement)
    transfer.service.ts     # TransferService (initiateTransfer)
    api.ts                  # Exports
  model/
    clientSummaryDto.ts
    clientDetailDto.ts
    accountInfoDto.ts
    transferRequest.ts
    ...
```

### Usage in components

```typescript
import { ClientService, ClientSummaryDto } from '../../generated';

export class DashboardComponent implements OnInit {
  constructor(private clientService: ClientService) {}

  ngOnInit() {
    this.clientService.listClients().subscribe(clients => {
      // type safety: clients: ClientSummaryDto[]
    });
  }
}
```

The `provideApi({ basePath: environment.apiBasePath })` in `app.config.ts` configures the API base URL. In development mode this is `http://localhost:8080`, in production mode it's an empty string (the proxy handles it).

---

## Component Responsibilities

### AppComponent (`app.ts` / `app.html`)

The **root component**. Responsibilities:
- Main navigation bar (MatToolbar) -- Dashboard, Clients, Transfer links
- Language selector menu (hu/en/de)
- `<router-outlet>` -- the active page loads here

### DashboardComponent

The **overview page**. Responsibilities:
- Display customer count
- Display aggregated account count
- Quick navigation (Client list, New transfer)
- Loading indicator (spinner)

Signals: `loading`, `clients`, `totalAccounts`

### ClientListComponent

The **customer list page**. Responsibilities:
- Display all customers in a table (MatTable)
- New customer registration form (toggleable)
- Navigation to customer detail view

Signals: `clients`, `showForm`

### ClientDetailComponent

The **customer detail view**. Responsibilities:
- Display customer data (name, tax ID, email, phone, status, creation date)
- Accounts table
- Deposit dialog (DepositDialogComponent)
- Withdrawal dialog (DepositDialogComponent -- in `withdraw` mode)
- New account opening dialog (NewAccountDialogComponent)
- Navigation to account statement (StatementComponent)

Signals: `client`

### TransferComponent

The **transfer page**. Responsibilities:
- Sender and receiver account selection (all customers' accounts visible)
- Amount and currency input
- Initiate transfer
- Display result (success/failure)
- Automatic currency setting based on sender account

Signals: `accounts`, `result`, `error`, `loading`

### StatementComponent

The **account statement page**. Responsibilities:
- Account data (account number, status, currency, balance)
- Transaction history table
- Direction indication with icons (incoming/outgoing/deposit/withdrawal)
- Amount coloring (+green / -red)
- Back navigation to customer detail view

Signals: `statement`

### DepositDialogComponent (dialog)

The **deposit/withdrawal dialog**. Responsibilities:
- Display account data
- Amount field
- Reason field (optional)
- Mode display (deposit / withdrawal)

### NewAccountDialogComponent (dialog)

The **new account opening dialog**. Responsibilities:
- Account number field
- Account type selection (CHECKING / SAVINGS)
- Currency selection (HUF / EUR / USD)
- Initial balance field

---

## Material Design Components

The project uses Angular Material for the UI:

| Component | Usage |
|---|---|
| `MatToolbar` | Main navigation bar |
| `MatButton` | Buttons everywhere |
| `MatIcon` | Icons (Material Icons) |
| `MatCard` | Dashboard cards, customer data, transfer form |
| `MatTable` | Customer list, account list, transaction list |
| `MatFormField` + `MatInput` | Form fields |
| `MatSelect` | Dropdown lists (account type, currency) |
| `MatDialog` | Deposit/withdrawal/account opening dialogs |
| `MatSnackBar` | Success/failure operation notifications |
| `MatMenu` | Language selector menu |
| `MatProgressSpinner` | Loading indicator |
| `MatChips` | Status badges |
| `MatTooltip` | Hover info |

---

## Internationalization (i18n) with ngx-translate

### How it works

The `@ngx-translate/core` provides runtime language switching capability -- no need to rebuild the application.

Configuration in `app.config.ts`:

```typescript
provideTranslateService({ fallbackLang: 'hu' }),
provideTranslateHttpLoader()
```

Language files are in the `src/assets/i18n/` directory:
- `hu.json` -- Hungarian
- `en.json` -- English
- `de.json` -- German

### Language switching in the root component

```typescript
export class App {
  currentLang = 'hu';

  constructor(private translate: TranslateService) {
    translate.addLangs(['hu', 'en', 'de']);
    translate.setDefaultLang('hu');
    translate.use('hu');
  }

  switchLang(lang: string) {
    this.currentLang = lang;
    this.translate.use(lang);
  }
}
```

### Usage in templates

```html
{{ 'dashboard.title' | translate }}
{{ 'nav.clients' | translate }}
{{ 'transfer.send' | translate }}
```

The `translate` pipe replaces the key with the current language's corresponding value.

---

## nginx Proxy Configuration

### Dev environment

In development, the Angular CLI proxy handles API forwarding:

**proxy.conf.json:**
```json
{
  "/api/*": {
    "target": "http://localhost:8080",
    "secure": false
  }
}
```

Started with: `ng serve --proxy-config proxy.conf.json`

### Container environment

In the container, nginx serves the static frontend files and proxies API calls to the backend:

The nginx configuration includes a `location /api/` block that reverse-proxies requests to `backend:8080`. This way the frontend and backend can be accessed through the same origin (no CORS issues).

### Docker images

The frontend uses a two-stage build:
1. **Node.js** (UBI9) -- `npm install` + `ng build` (Dockerfile.node)
2. **nginx** (UBI9) -- copies `dist/` and serves static files + API proxy (Dockerfile.nginx)

### Dev vs Container Environments

| Aspect | Dev | Container |
|---|---|---|
| **Server** | Webpack dev server (port 4200) | nginx (port 8080 in container) |
| **API proxy** | `proxy.conf.json` -> `localhost:8080` | nginx `location /api/` -> `backend:8080` |
| **Hot reload** | Yes (file watcher) | No (requires rebuild) |
| **API base path** | `http://localhost:8080` | Empty string (same origin via nginx proxy) |
| **Build command** | `ng serve` | `ng build --configuration=production` |
| **Output** | In-memory | `dist/` directory -> served by nginx |

---

## Applied Enterprise Patterns

### 1. OpenAPI Contract-First

The API contract is the starting point -- not the implementation. The `backend-api.yaml` specification generates the TypeScript client. This ensures type safety and contract compliance.

### 2. Type-safe API Clients

The generated services (e.g., `ClientService`, `TransferService`) return typed responses. If the API changes, the TypeScript compiler flags the error.

### 3. Lazy Loading

Every page loads as a separate chunk -- the initial bundle only contains the main navigation.

### 4. Internationalization (i18n)

Three language support with runtime switching capability. Language files are in JSON format, easily extensible.

---

## Further Reading

- [Architecture Overview](overview.md)
- [Future Patterns and Roadmap](future-patterns.md)
