# Bank Demo — Quarkus Microservices

Banking demo application: customer management, accounts, transfers. Quarkus backend (BFF + 3 microservices), Angular frontend, PostgreSQL.

**Version:** 1.1.0

## Architecture

```
Frontend (Angular/nginx :4200)
    ↓
Backend BFF (:8080)
    ├→ Customer Service (:8081)
    ├→ Account Service (:8082)
    └→ Transaction Service (:8083)
         ↓
      PostgreSQL (:5432)
```

See [Architecture Overview](docs/architecture/overview.md) for detailed design, API specs, and enterprise patterns.

## Project Structure

```
quarkus_demo/
  services/                  Microservices (behind the BFF)
    customer-service/          Customer CRUD (:8081)
    account-service/           Account management (:8082)
    transaction-service/       Transaction processing (:8083)
  backend/                   BFF — API gateway (:8080)
  frontend/                  Angular + nginx (:4200)
  openapi-specs/             OpenAPI YAML contracts (single source of truth)
  deployment/
    local-manual/              Docker Compose: PostgreSQL + pgAdmin
    local-docker/              Docker Compose: full stack in containers
    redhat-sandbox/            OpenShift manifests + Tekton CI/CD pipeline
  bank-demo-ctl              CLI deployment tool
  docs/                      Documentation
```

## Running the Application

All environments are managed by `bank-demo-ctl`:

```bash
./bank-demo-ctl help
```

### Local Manual — development with hot-reload

PostgreSQL + pgAdmin in Docker, Quarkus services in `quarkus:dev` mode with live coding.

```bash
./bank-demo-ctl up local-manual      # starts DB + all services
./bank-demo-ctl status local-manual
./bank-demo-ctl down local-manual
```

URLs: frontend `http://localhost:4200`, Swagger `http://localhost:8080/q/swagger-ui`, pgAdmin `http://localhost:5050`

### Local Docker — full stack in containers

Everything runs in Docker Compose. No hot-reload — tests containerized builds.

```bash
./bank-demo-ctl up local-docker
./bank-demo-ctl status local-docker
./bank-demo-ctl down local-docker
```

URLs: frontend `http://localhost:4200`, Swagger `http://localhost:8080/q/swagger-ui`

### Red Hat Developer Sandbox — OpenShift

Builds and deploys via Tekton pipeline. Git push triggers automatically.

```bash
./bank-demo-ctl up redhat-sandbox --infra   # infrastructure setup (DB, pipeline, webhook)
./bank-demo-ctl up redhat-sandbox           # infra + pipeline run
./bank-demo-ctl status redhat-sandbox
./bank-demo-ctl down redhat-sandbox         # removes everything (PVCs remain)
./bank-demo-ctl clean redhat-sandbox        # removes PVCs too
```

See [Sandbox Setup Guide](docs/openshift/sandbox-setup.md) for detailed instructions.

## Documentation

| Topic | Link |
|---|---|
| Architecture & patterns | [docs/architecture/overview.md](docs/architecture/overview.md) |
| Frontend architecture | [docs/architecture/frontend.md](docs/architecture/frontend.md) |
| Future patterns & roadmap | [docs/architecture/future-patterns.md](docs/architecture/future-patterns.md) |
| Local environment setup | [docs/guides/local-setup.md](docs/guides/local-setup.md) |
| Running modes guide | [docs/guides/running.md](docs/guides/running.md) |
| OpenShift concepts | [docs/openshift/concepts.md](docs/openshift/concepts.md) |
| Sandbox setup | [docs/openshift/sandbox-setup.md](docs/openshift/sandbox-setup.md) |
| Tekton pipeline details | [docs/openshift/pipeline.md](docs/openshift/pipeline.md) |

## Tech Stack

- **Backend**: Quarkus 3.34, Java 21, Liquibase, RESTEasy, MicroProfile REST Client
- **Frontend**: Angular 19, Angular Material, ngx-translate, nginx
- **Database**: PostgreSQL 17 (schema-per-service isolation)
- **API**: OpenAPI 3.0 contract-first (code generation for server + client)
- **CI/CD**: Tekton Pipelines on OpenShift
- **Container images**: Red Hat UBI9 (OpenJDK 21, nginx 1.24, Node.js 22)
