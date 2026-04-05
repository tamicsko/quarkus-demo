# Running Guide — 3 Modes

All environments are managed via `bank-demo-ctl` from the project root.

## bank-demo-ctl Commands

| Command | Description |
|---------|-------------|
| `./bank-demo-ctl status <mode>` | Show running containers / pods |
| `./bank-demo-ctl up <mode>` | Start environment |
| `./bank-demo-ctl down <mode>` | Stop environment |
| `./bank-demo-ctl logs <mode>` | Tail logs |
| `./bank-demo-ctl clean <mode>` | Remove containers and volumes |

Replace `<mode>` with `local-manual`, `local-docker`, or `redhat-sandbox`.

---

## 1. Dev Mode (local-manual — Local Development)

**What runs where:** PostgreSQL in Docker, everything else from the CLI. Hot-reload, fast iteration.

### Prerequisites
```
Java 21, Maven, Node 24, Docker Desktop (or Podman) running
```

### Steps

**1. Start PostgreSQL + pgAdmin:**
```bash
./bank-demo-ctl up local-manual
```

This starts:
- PostgreSQL on `localhost:5432` (user: `demo`, password: `demo`, db: `quarkus_demo`)
- pgAdmin on `localhost:5050` (login: `admin@demo.dev` / `admin`)

The `init-schemas.sql` runs automatically on first startup and creates the 3 schemas: `customer_svc`, `account_svc`, `tx_svc`.

**2. Verify that PostgreSQL is running:**
```bash
docker ps
# You should see: bank_demo_db (postgres:17) and bank_demo_pgadmin (pgadmin4)
```

**3. Start each Quarkus service in a separate terminal:**

Terminal 1 — Customer Service (:8081):
```bash
./mvnw quarkus:dev -pl services/customer-service
```

Terminal 2 — Account Service (:8082):
```bash
./mvnw quarkus:dev -pl services/account-service
```

Terminal 3 — Transaction Service (:8083):
```bash
./mvnw quarkus:dev -pl services/transaction-service
```

Terminal 4 — Backend BFF (:8080):
```bash
./mvnw quarkus:dev -pl backend
```

**Important:** The order matters! The Transaction Service needs the Account Service, and the Backend needs all three. In Quarkus dev mode this does not block (lazy connection), but API calls only work when the target service is running.

Each service on startup:
- Liquibase runs the migrations (creates tables in its own schema)
- Swagger UI available at: `http://localhost:{port}/q/swagger-ui`
- Health check: `http://localhost:{port}/q/health`

**4. Angular frontend:**

Terminal 5:
```bash
cd frontend
ng serve
```
Available at: `http://localhost:4200`

The frontend sends API requests to `http://localhost:8080` (configured in `environment.ts`).

**5. Testing via Swagger UI:**

- Customer Service: `http://localhost:8081/q/swagger-ui` — create a customer
- Account Service: `http://localhost:8082/q/swagger-ui` — open an account
- Backend (BFF): `http://localhost:8080/q/swagger-ui` — try the registration which calls both

**6. Shutdown:**

Press `Ctrl+C` in each terminal (Quarkus dev stops automatically), then:
```bash
./bank-demo-ctl down local-manual
```
If you also want to delete DB data (clean restart):
```bash
./bank-demo-ctl clean local-manual
```
The `clean` command removes the `pgdata` volume as well.

---

## 2. Container Mode (local-docker — Production Simulation)

**What runs where:** EVERYTHING in containers. No CLI, no hot-reload. You are testing whether the built application works in containers.

### Prerequisites
```
Docker Desktop (or Podman + podman-compose)
The Java applications must be built FIRST (JAR)
```

### Steps

**1. Build Java modules (create JARs):**
```bash
./mvnw package -DskipTests
```
This creates the `target/quarkus-app/` directory in every module (the Dockerfile works from this).

**2. Create secrets:**
```bash
mkdir -p deployment/local-docker/secrets
echo "MySecureDbPass123" > deployment/local-docker/secrets/db_password.txt
```

**3. Create `.env` file:**
```bash
cp deployment/local-docker/.env.example deployment/local-docker/.env
```
Edit the `.env` file, enter the passwords:
```
CUSTOMER_SVC_DB_PASSWORD=MySecureDbPass123
ACCOUNT_SVC_DB_PASSWORD=MySecureDbPass123
TX_SVC_DB_PASSWORD=MySecureDbPass123
```

**4. Start the full system:**
```bash
./bank-demo-ctl up local-docker
```

### What Happens Behind the Scenes

```
bank-demo-ctl up local-docker
  |
  +-- postgres (postgres:17)
  |     +-- init-schemas.sql runs -> 3 schemas created
  |     +-- healthcheck: pg_isready -> green
  |
  +-- customer-service (Quarkus JVM, UBI9/OpenJDK 21)
  |     +-- waits for postgres healthcheck
  |     +-- QUARKUS_PROFILE=container -> application-container.properties
  |     +-- Liquibase migrates -> customer_svc.customer table
  |
  +-- account-service (Quarkus JVM)
  |     +-- Liquibase -> account_svc.account + balance_history
  |
  +-- transaction-service (Quarkus JVM)
  |     +-- Liquibase -> tx_svc.transaction
  |     +-- ACCOUNT_SERVICE_URL=http://account-service:8082
  |
  +-- backend (Quarkus JVM) -> port 8080 exposed
  |     +-- receives 3 service URLs from env vars
  |
  +-- frontend (nginx) -> port 4200 exposed
        +-- Angular build ran in the multi-stage Dockerfile
        +-- nginx.conf: /api/* -> proxy_pass backend:8080
```

**5. Verification:**
```bash
# Are all containers running?
./bank-demo-ctl status local-docker

# View logs (all services):
./bank-demo-ctl logs local-docker

# Specific service log:
docker compose -f deployment/local-docker/docker-compose.yaml logs -f transaction-service

# Health check:
curl http://localhost:8080/q/health
```

**6. Accessible URLs:**
- Frontend: `http://localhost:4200`
- Backend Swagger UI: `http://localhost:8080/q/swagger-ui`
- pgAdmin: `http://localhost:5050`
- (The individual services are NOT accessible externally — only on the `bank-net` internal network)

**7. Shutdown:**
```bash
./bank-demo-ctl down local-docker       # stop containers
./bank-demo-ctl clean local-docker      # + delete volumes (DB data too!)
```

**8. If you change the code and want to rebuild:**
```bash
./mvnw package -DskipTests              # Java recompile
./bank-demo-ctl up local-docker         # image rebuild + start
```

---

## 3. OpenShift Mode

**What runs where:** Everything in an OpenShift/Kubernetes cluster. OpenShift manages the pods: health checks, restarts, scaling, routing.

### Prerequisites

**Option A — CodeReady Containers (local):**
```bash
# 1. Install CRC: https://console.redhat.com/openshift/create/local
# 2. Start it:
crc setup        # one-time, downloads the OpenShift VM (~3GB)
crc start        # starts the VM (9+ GB RAM needed!)
# 3. Login setup:
eval $(crc oc-env)   # set oc CLI path
oc login -u developer -p developer https://api.crc.testing:6443
```

**Option B — Red Hat Developer Sandbox (cloud, zero setup):**
```
1. Register: https://developers.redhat.com/developer-sandbox
2. Get the login command from the web console:
   oc login --token=sha256~XXXXX --server=https://api.sandbox-XXX.openshiftapps.com:6443
```

> **For detailed step-by-step Sandbox instructions see: [Sandbox Setup](../openshift/sandbox-setup.md)**
> The Sandbox has separate YAMLs and BuildConfigs: `deployment/redhat-sandbox/`

### Steps

**1. Deploy infrastructure:**
```bash
./bank-demo-ctl infra redhat-sandbox    # creates PostgreSQL, secrets, configmaps
```

**2. Build and deploy:**
```bash
./bank-demo-ctl up redhat-sandbox       # triggers pipeline build + deploy
```

**3. Verification:**
```bash
# All pod statuses
oc get pods
# NAME                                   READY   STATUS    RESTARTS   AGE
# postgres-0                             1/1     Running   0          2m
# customer-service-xxx-yyy               1/1     Running   0          1m
# account-service-xxx-yyy                1/1     Running   0          1m
# transaction-service-xxx-yyy            1/1     Running   0          1m
# backend-xxx-yyy                        1/1     Running   0          45s
# frontend-xxx-yyy                       1/1     Running   0          30s

# Routes (external URLs)
oc get routes
# NAME          HOST/PORT                              PATH   SERVICES   PORT
# bank-demo     bank-demo-{ns}.apps.{cluster}                 frontend   80
# backend-api   backend-api-{ns}.apps.{cluster}               backend    8080
```

**4. Useful `oc` commands:**
```bash
# Scaling (2 backend instances)
oc scale deployment/backend --replicas=2

# Rolling update (new image deploy)
oc rollout restart deployment/backend

# Shell into a container (debugging)
oc rsh deployment/customer-service

# View pod logs
oc logs -f deployment/transaction-service

# Delete all resources
oc delete all --all
oc delete secrets --all
oc delete configmap --all
```

---

## Deployment Modes Comparison

| | Dev Mode | Docker Compose | OpenShift |
|---|---|---|---|
| **When to use** | During development | "Does it work in containers?" | Prod / demo |
| **Quarkus profile** | `dev` (auto) | `container` | `container` |
| **PostgreSQL** | Docker container | Docker container | Pod (StatefulSet) |
| **Services** | CLI (`quarkus dev`) | Docker container | Pod (Deployment) |
| **Frontend** | `ng serve` | nginx container | nginx Pod |
| **Hot reload** | Yes | No | No |
| **Health check** | Swagger UI | `docker compose ps` | `oc get pods` (auto-restart!) |
| **Secrets** | Plaintext properties | Docker secrets file | K8s Secret object |
| **Scaling** | -- | -- | `oc scale --replicas=N` |
| **Startup** | 5 terminals | 1 command | `oc apply` / `bank-demo-ctl` |

---

## Troubleshooting Common Issues

### Quarkus service fails to start in dev mode
- Check that PostgreSQL is running: `docker ps`
- Check that `JAVA_HOME` points to JDK 21
- Check that the port is not already in use (8080-8083)

### Docker Compose: container keeps restarting
- Check logs: `docker compose logs -f <service-name>`
- Usually a database connection issue — make sure the `.env` file exists and passwords match
- If Liquibase fails, try a clean restart: `./bank-demo-ctl clean local-docker`

### Frontend cannot reach the backend
- Dev mode: ensure the backend is running on port 8080
- Docker mode: the `proxy_pass` in nginx.conf must point to `backend:8080` (Docker service name)
- Check CORS configuration in `application.properties`

### "Port already in use" error
- Find and kill the process: `netstat -ano | findstr :8080` then `taskkill /F /PID <pid>`
- Or use a different port by setting `quarkus.http.port` in dev mode

## Access Points (local)

| Service | URL |
|---------|-----|
| Frontend | http://localhost:4200 |
| BFF Swagger UI | http://localhost:8080/q/swagger-ui |
| Customer Service Swagger UI | http://localhost:8081/q/swagger-ui |
| Account Service Swagger UI | http://localhost:8082/q/swagger-ui |
| Transaction Service Swagger UI | http://localhost:8083/q/swagger-ui |
| pgAdmin | http://localhost:5050 |

## Prerequisites

- [Local Setup](local-setup.md)
