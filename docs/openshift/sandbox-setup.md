# Red Hat Developer Sandbox — Step by Step

## What Is This?

The Red Hat Developer Sandbox is a **free, cloud-hosted OpenShift cluster**. You don't need to install anything on your machine (except the `oc` CLI), you don't need to run a VM, you don't need 35 GB of free space. Red Hat gives you a namespace for 30 days where you can run your application.

### Sandbox vs. CRC (CodeReady Containers) Comparison

| | Sandbox | CRC (local) |
|---|---|---|
| **Where it runs** | Red Hat's cloud | Your machine (VM) |
| **Resource requirements** | Just internet + `oc` CLI | 9+ GB RAM, 35 GB disk |
| **Setup time** | ~5 minutes | ~30 minutes |
| **Admin rights** | None (only your namespace) | Full (entire cluster) |
| **Namespace** | Fixed: `{username}-dev` | Any |
| **Lifespan** | 30 days (renewable) | As long as the VM lives |
| **Cost** | Free | Free |

### Sandbox Limitations
- **You cannot create a new namespace** — `{username}-dev` is yours
- **Resource quotas**: ~3 CPU / 30 GB RAM for deploy, 80 GB storage — more than enough
- **30-day expiration** — afterwards you must renew (data is lost!)
- **No cluster-admin rights** — you cannot install operators

---

## Prerequisites

```
1. Red Hat account (free registration)
2. Activated Developer Sandbox
3. oc CLI installed and on PATH
4. Java 21 + Maven (for local builds)
5. Docker Desktop OR Podman (NOT required! Image builds run on OpenShift)
```

> **Important:** For the Sandbox approach you do NOT need Docker on your machine! The images are built by OpenShift's build system in the cloud. We only upload the source code/JARs.

---

## Stage 1 — Sandbox Registration and Login

**1.1. Create a Red Hat account** (if you don't have one yet):
```
https://developers.redhat.com/register
```

**1.2. Activate the Sandbox:**
```
https://sandbox.redhat.com -> OpenShift -> "Try it"
```
Wait ~2 minutes while the cluster is provisioned.

**1.3. Open the web console:**

On the Sandbox dashboard, click the OpenShift link. You get the web console:
```
https://console-openshift-console.apps.{cluster}.openshiftapps.com
```

---

## Stage 2 — `oc` CLI Installation

**Windows:**
```bash
# Download
curl -sL "https://mirror.openshift.com/pub/openshift-v4/clients/ocp/stable/openshift-client-windows.zip" -o oc.zip

# Extract (e.g. to C:\tools\oc\)
mkdir C:\tools\oc
# Extract oc.exe from oc.zip to this directory

# Add to PATH (PowerShell, admin):
[Environment]::SetEnvironmentVariable('Path', $env:Path + ';C:\tools\oc', 'User')

# Verify (in a new terminal):
oc version --client
```

**Linux/Mac:**
```bash
# Linux
curl -sL https://mirror.openshift.com/pub/openshift-v4/clients/ocp/stable/openshift-client-linux.tar.gz | tar xz -C /usr/local/bin oc

# Mac
brew install openshift-cli
```

---

## Stage 3 — `oc login`

In the web console:
1. Top right corner -> click your name
2. **"Copy login command"**
3. **"Display Token"**
4. Copy and run:

```bash
oc login --token=sha256~XXXXX --server=https://api.{cluster}.openshiftapps.com:6443
```

Verification:
```bash
oc whoami           # -> tamicsko
oc project          # -> tamicsko-dev
```

> **The token expires periodically!** If you get an `Unauthorized` error, go back to the web console and request a new token.

---

## Stage 4 — Secrets Setup

The `deployment/redhat-sandbox/secrets.yaml` file has passwords with `CHANGE_ME` placeholders. **Replace them before deploying!**

```bash
# Base64 encode a password:
printf 'MySecurePass123' | base64
# Result: TXlTZWN1cmVQYXNzMTIz

# Edit secrets.yaml and replace the Q0hBTkdFX01F values
# with your own base64-encoded password
```

Or in one step with the `oc` CLI (no need to edit YAML):
```bash
oc create secret generic db-credentials \
  --from-literal=username=bankadmin \
  --from-literal=password=MySecurePass123 \
  --dry-run=client -o yaml | oc apply -f -

oc create secret generic customer-svc-db \
  --from-literal=password=MySecurePass123 \
  --dry-run=client -o yaml | oc apply -f -

oc create secret generic account-svc-db \
  --from-literal=password=MySecurePass123 \
  --dry-run=client -o yaml | oc apply -f -

oc create secret generic tx-svc-db \
  --from-literal=password=MySecurePass123 \
  --dry-run=client -o yaml | oc apply -f -
```

---

## Stage 5 — ConfigMap and PostgreSQL Deploy

```bash
# ConfigMap (service URLs, DB host)
oc apply -f deployment/redhat-sandbox/configmaps.yaml

# PostgreSQL (StatefulSet + Service + init SQL)
oc apply -f deployment/redhat-sandbox/postgres.yaml

# Wait for it to start:
oc get pods -w
# postgres-0   1/1   Running   0   ~30s
# (Ctrl+C when READY)
```

### What Happens Here?
- A **StatefulSet** with 1 Postgres pod is created
- The pod gets a **1 GB PersistentVolume** (data survives pod restarts)
- `init.sql` runs: creates the 3 schemas (`customer_svc`, `account_svc`, `tx_svc`)
- The **headless Service** (`clusterIP: None`) ensures that services can reach it at `postgres:5432`

---

## Stage 6 — Build Java Modules (Locally)

```bash
cd D:\projects\quarkus_demo
./mvnw package -DskipTests
```

This creates the `target/quarkus-app/` directory in every module. Only this directory is uploaded to OpenShift.

---

## Stage 7 — Build Images on OpenShift (Binary Build)

### What Is a Binary Build?

Normally OpenShift would pull code from a git repo and build it. With a **Binary Build**, you upload the already-built files (JARs) from your local machine, and OpenShift only creates the image from them.

```
[Local machine]                    [OpenShift Sandbox]

 mvnw package
   |
 target/quarkus-app/  --push-->  BuildConfig (Dockerfile)
                                    |
                                  ImageStream (internal registry)
                                    |
                                  Deployment (pod)
```

### Creating BuildConfigs (One-time Step)

> **Important lesson learned:** The `oc new-build --binary --dockerfile='...'` combination **does not work reliably** — the `--binary` and `--dockerfile` flags together do not correctly pass the Dockerfile content. Instead, **YAML BuildConfig** must be used, where the `source.type: Binary` and `source.dockerfile` fields are defined together. See the YAML files in the `deployment/redhat-sandbox/` directory for the correct approach.

Each Java service needs a YAML BuildConfig:
```yaml
apiVersion: build.openshift.io/v1
kind: BuildConfig
metadata:
  name: customer-service
spec:
  source:
    type: Binary
    dockerfile: |
      FROM registry.access.redhat.com/ubi8/openjdk-21:1.20
      ENV LANGUAGE="en_US:en"
      COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
      COPY --chown=185 target/quarkus-app/*.jar /deployments/
      COPY --chown=185 target/quarkus-app/app/ /deployments/app/
      COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/
      EXPOSE 8080
      ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
      ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
  strategy:
    type: Docker
    dockerStrategy: {}
  output:
    to:
      kind: ImageStreamTag
      name: customer-service:latest
```

Repeat for: `account-service`, `transaction-service`, `backend` (same Dockerfile, only `metadata.name` and `output` change).

Frontend:
```bash
oc new-build --binary --name=frontend --strategy=docker
```
(The frontend uses its own Dockerfile, no inline Dockerfile needed.)

### Starting Builds (Repeat This When Code Changes)

```bash
# Java services — upload the target/quarkus-app directory
oc start-build customer-service --from-dir=./customer-service/target/quarkus-app --follow
oc start-build account-service --from-dir=./account-service/target/quarkus-app --follow
oc start-build transaction-service --from-dir=./transaction-service/target/quarkus-app --follow
oc start-build backend --from-dir=./backend/target/quarkus-app --follow

# Frontend — the entire frontend directory (has its own Dockerfile)
oc start-build frontend --from-dir=./frontend --follow
```

The `--follow` shows the build log in real time. At the end of each build:
```
Push successful
```

This means the image is ready and in the internal registry:
`image-registry.openshift-image-registry.svc:5000/tamicsko-dev/{service-name}:latest`

---

## Stage 8 — Apply Deployments

```bash
# Services (can start in parallel)
oc apply -f deployment/redhat-sandbox/customer-service.yaml
oc apply -f deployment/redhat-sandbox/account-service.yaml
oc apply -f deployment/redhat-sandbox/transaction-service.yaml

# Backend
oc apply -f deployment/redhat-sandbox/backend.yaml

# Frontend
oc apply -f deployment/redhat-sandbox/frontend.yaml
```

Verification:
```bash
oc get pods
# NAME                                   READY   STATUS    RESTARTS   AGE
# postgres-0                             1/1     Running   0          5m
# customer-service-xxx-yyy               1/1     Running   0          2m
# account-service-xxx-yyy                1/1     Running   0          2m
# transaction-service-xxx-yyy            1/1     Running   0          2m
# backend-xxx-yyy                        1/1     Running   0          1m
# frontend-xxx-yyy                       1/1     Running   0          1m
```

If a pod is in `CrashLoopBackOff` or `Error` status:
```bash
oc logs deployment/customer-service    # view logs
oc describe pod <pod-name>             # events, error details
```

---

## Stage 9 — Route URLs and Testing

```bash
oc get routes
# NAME          HOST                                                        PORT
# bank-demo     bank-demo-tamicsko-dev.apps.rm2.thpm.p1.openshiftapps.com   80
# backend-api   backend-api-tamicsko-dev.apps.rm2.thpm.p1.openshiftapps.com 8080
```

Accessible URLs (HTTPS — the Sandbox automatically provides TLS):
```
Frontend:    https://bank-demo-tamicsko-dev.apps.rm2.thpm.p1.openshiftapps.com
Swagger UI:  https://backend-api-tamicsko-dev.apps.rm2.thpm.p1.openshiftapps.com/q/swagger-ui
Health:      https://backend-api-tamicsko-dev.apps.rm2.thpm.p1.openshiftapps.com/q/health
```

---

## bank-demo-ctl Workflow

The `bank-demo-ctl` script simplifies the multi-step process:

```bash
# Deploy infrastructure (PostgreSQL, secrets, configmaps)
./bank-demo-ctl infra redhat-sandbox

# Build and deploy everything (triggers pipeline)
./bank-demo-ctl up redhat-sandbox

# Tear down
./bank-demo-ctl down redhat-sandbox
```

---

## Redeploying After Code Changes

If you changed Java code:
```bash
# 1. Rebuild locally
./mvnw package -DskipTests

# 2. Rebuild on OpenShift (only the modified service)
oc start-build customer-service --from-dir=./customer-service/target/quarkus-app --follow

# 3. The Deployment automatically restarts (ImageStream trigger)
#    If it doesn't, trigger manually:
oc rollout restart deployment/customer-service
```

If the frontend changed:
```bash
oc start-build frontend --from-dir=./frontend --follow
oc rollout restart deployment/frontend
```

> **Note:** After a build, the ImageStream trigger should automatically restart the Deployment, but this does not always happen immediately. If `oc start-build` succeeded but no new pod appears, trigger it manually:
> ```bash
> oc rollout restart deployment/customer-service
> ```
> This always forces a restart with the new image.

---

## GitHub Webhook Configuration

1. GitHub repo > Settings > Webhooks > Add webhook
2. Payload URL: `https://github-webhook-tamicsko-dev.apps.sandbox-m4.1530.p1.openshiftapps.com`
3. Content type: `application/json`
4. Events: Just the push event
5. Secret: (optional, but recommended in production environments)

Get the EventListener route URL:
```bash
oc get route github-webhook -o jsonpath='{.spec.host}'
```

Once configured, every `git push` triggers the Tekton pipeline automatically.

---

## DB Password Sync

If the sandbox rotates your credentials or you need to change them:

```bash
# Enter the PostgreSQL pod
oc rsh statefulset/postgres
psql -U postgres
ALTER USER bank_user WITH PASSWORD '<new-password>';
\q

# Update the secret
oc edit secret postgres-secret
```

Or recreate secrets via CLI:
```bash
oc create secret generic db-credentials \
  --from-literal=username=bankadmin \
  --from-literal=password=NewPassword123 \
  --dry-run=client -o yaml | oc apply -f -
```

---

## Useful `oc` Commands

```bash
# List all resources
oc get all

# Pod logs (live)
oc logs -f deployment/backend

# Shell into a pod (debugging)
oc rsh deployment/customer-service

# Scaling (2 backend instances)
oc scale deployment/backend --replicas=2

# Resource consumption
oc adm top pods

# Delete everything (clean slate)
oc delete all -l app.kubernetes.io/part-of=bank-demo
oc delete secret db-credentials customer-svc-db account-svc-db tx-svc-db
oc delete configmap service-config postgres-init
oc delete pvc --all
```

---

## Known Problems and Solutions

These problems were encountered during actual deployment. If you follow this guide, the YAML files already contain the fixes, but it is worth knowing about them.

### PostgreSQL `lost+found` Problem

**Symptom:** The PostgreSQL pod does not start, the log shows:
```
initdb: directory "/var/lib/postgresql/data" exists but is not empty
```

**Cause:** When the PersistentVolumeClaim is mounted directly to the `/var/lib/postgresql/data` path, the volume contains a `lost+found` directory (created by the filesystem). PostgreSQL sees this as a non-empty directory and refuses to initialize.

**Solution:** Use the `PGDATA` environment variable to specify a subdirectory:
```yaml
env:
  - name: PGDATA
    value: /var/lib/postgresql/data/pgdata
```
This way PostgreSQL creates the database in the `pgdata` subdirectory, and `lost+found` does not interfere.

**Related problem:** The healthcheck probe with `pg_isready -U bankadmin` does not work because `pg_isready` by default tries to connect to a database matching the username (`bankadmin`), which does not exist — only the `quarkus_demo` database is created. The correct probe command:
```yaml
livenessProbe:
  exec:
    command:
      - pg_isready
      - -U
      - bankadmin
      - -d
      - quarkus_demo
```

### nginx "permission denied" on OpenShift

**Symptom:** The frontend pod is in `CrashLoopBackOff` status, the log shows:
```
nginx: [emerg] open() "/var/cache/nginx/..." failed (13: Permission denied)
```

**Cause:** Due to OpenShift's security policy, containers run as a **non-root user** with a random UID. The standard `nginx:alpine` image requires root permissions for writing to `/var/cache/nginx/` and `/var/run/`.

**Solution:** Use the `nginxinc/nginx-unprivileged:alpine` image instead of the standard `nginx:alpine`. This image is configured to work as a non-root user. Important change: the listen port becomes **8080 instead of 80**, so `nginx.conf` and Service/Route definitions must use 8080:
```nginx
server {
    listen 8080;
    ...
}
```

In our project we use the Red Hat UBI nginx image (`ubi9/nginx-124:1`) which is also rootless and listens on 8080.

### ReplicaSet Quota Exhaustion

**Symptom:** Deploy fails with a "quota exceeded" error referring to ReplicaSets.

**Cause:** The Developer Sandbox allows 100 ReplicaSets per namespace. Every `rollout restart` creates a new ReplicaSet, and old ones remain (with 0 replicas). Over time you reach the 100 limit.

**Solution:** Periodic cleanup:
```bash
# Delete 0-replica ReplicaSets
oc get rs -o json | jq -r '.items[] | select(.spec.replicas==0) | .metadata.name' | xargs oc delete rs
```

Also reduce with `revisionHistoryLimit` in Deployments:
```yaml
spec:
  revisionHistoryLimit: 5  # Keep only 5 previous versions
```

---

## Comprehensive Troubleshooting Guide

### "Unauthorized" on `oc` commands
The token expired. Go to the web console -> Copy login command -> Display Token -> run the new `oc login` command.

### Pod won't start — `ImagePullBackOff`
The binary build did not run or failed. Check:
```bash
oc get builds                          # build list
oc logs build/customer-service-1       # build log
```

### Pod won't start — `CrashLoopBackOff`
The application starts but immediately stops. Usually a DB connection problem:
```bash
oc logs deployment/customer-service    # what is the error?
oc get pods                            # is postgres-0 running?
```

### Build is slow or times out
The Sandbox runs on shared resources. During peak times it can be slower. The first Java image build takes ~3-5 minutes (downloading the base image), afterwards it is faster.

### "quota exceeded" error
You are requesting too many resources. Reduce the resource limits in the YAMLs, or delete unused pods.

### Pod events show `FailedScheduling`
The cluster does not have enough resources to schedule the pod. Scale down other deployments or wait.

### Liquibase migration fails
Check that:
1. PostgreSQL pod is running and healthy
2. The schema exists (init SQL ran successfully)
3. The database password in the Secret matches the one PostgreSQL was initialized with

```bash
# Verify DB connectivity from inside a pod
oc rsh deployment/customer-service
curl -v telnet://postgres:5432
```

---

## Daily Workflow

| Step | Command |
|------|---------|
| Login (token may have expired) | `oc login --token=... --server=...` |
| Check status | `./bank-demo-ctl status redhat-sandbox` |
| View pipeline runs | `oc get pipelineruns` |
| Tail logs | `./bank-demo-ctl logs redhat-sandbox` |
| Manual trigger | `./bank-demo-ctl up redhat-sandbox` |
| View all resources | `oc get all` |
| Check resource usage | `oc adm top pods` |

## Further Reading

- [Pipeline Details](pipeline.md)
- [OpenShift Concepts](concepts.md)
