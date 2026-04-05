# Tekton CI/CD Pipeline Details

## What Is Tekton?

Tekton is a cloud-native, Kubernetes-native CI/CD framework. It was originally part of the Knative project started by Google, then became an independent project. Its main advantage is that every pipeline element (Task, Pipeline, Trigger) is definable as a Kubernetes CRD (Custom Resource Definition) — so it can be managed just like any other Kubernetes resource.

### Tekton vs. Other CI/CD Tools

| Feature | Tekton | Jenkins | GitHub Actions | GitLab CI |
|---------|--------|---------|----------------|-----------|
| **Execution** | Kubernetes Pods | Dedicated server / VM | GitHub cloud / self-hosted | GitLab cloud / self-hosted |
| **Definition** | Kubernetes YAML (CRD) | Groovy (Jenkinsfile) | YAML (.github/workflows) | YAML (.gitlab-ci.yml) |
| **Scalability** | Automatic (K8s based) | Manual (add agents) | Cloud: automatic | Cloud: automatic |
| **Vendor lock-in** | None (any K8s) | None (but own FS) | Tied to GitHub | Tied to GitLab |
| **Image build** | Buildah/Kaniko (rootless) | Docker daemon | Docker daemon | Docker daemon / Kaniko |
| **OpenShift** | Natively supported (Operator) | Plugin | Separate setup | Separate setup |
| **Reusability** | ClusterTask / TaskHub | Shared Libraries | Marketplace Actions | CI/CD Templates |
| **UI** | Tekton Dashboard / OpenShift | Built-in | Built-in | Built-in |

### Why Tekton in This Project?

1. **OpenShift Pipelines Operator**: In OpenShift, Tekton is the "first-class" CI/CD solution — installable with one click
2. **Kubernetes-native**: Every pipeline element runs as a Pod, so we can leverage K8s resource management
3. **Rootless build**: Buildah rootless image building perfectly fits the OpenShift security model
4. **Shared tasks**: Pre-defined tasks are available in the `openshift-pipelines` namespace

---

## Tekton Core Concepts

### Step
The smallest execution unit. Running a single container from a given image with a given command. Steps within a Task run **sequentially** on a shared workspace.

### Task
A logical group of one or more Steps. Tasks run as separate Pods (every Task = 1 Pod, every Step = 1 container within the Pod).

```yaml
apiVersion: tekton.dev/v1
kind: Task
metadata:
  name: maven-java21
spec:
  workspaces:
    - name: source
  steps:
    - name: maven-build
      image: registry.access.redhat.com/ubi9/openjdk-21:1.20
      script: |
        #!/bin/bash
        bash mvnw package -DskipTests
```

### TaskRun
A single execution of a Task. In practice, the Pipeline creates it automatically, but for testing it can be started manually.

```bash
# Start a manual TaskRun
tkn task start maven-java21 --workspace name=source,claimName=pipeline-workspace
```

### Pipeline
Linking multiple Tasks together — you can specify the order (`runAfter`), parameters, and shared workspaces.

```yaml
apiVersion: tekton.dev/v1
kind: Pipeline
metadata:
  name: bank-demo-pipeline
spec:
  workspaces:
    - name: shared-workspace
  tasks:
    - name: git-clone
      taskRef:
        resolver: cluster
        params:
          - name: kind
            value: task
          - name: name
            value: git-clone
          - name: namespace
            value: openshift-pipelines
      workspaces:
        - name: output
          workspace: shared-workspace
    - name: maven-build
      runAfter: [git-clone]
      taskRef:
        name: maven-java21
      workspaces:
        - name: source
          workspace: shared-workspace
```

### PipelineRun
A single execution of a Pipeline. The trigger creates it automatically, but it can also be started manually.

```bash
# Start a manual PipelineRun
tkn pipeline start bank-demo-pipeline \
  --workspace name=shared-workspace,claimName=pipeline-workspace \
  --param git-url=https://github.com/tamicsko/quarkus-demo.git \
  --param git-revision=main
```

### Workspace
Shared filesystem between Tasks. Typically points to a PersistentVolumeClaim (PVC), which allows files created by one Task (e.g. Git repo, Maven build output) to be accessed by the next Task.

```yaml
workspaces:
  - name: shared-workspace
    persistentVolumeClaim:
      claimName: pipeline-workspace
```

**Important**: All Tasks mount the same PVC, so the source code downloaded by `git-clone` is accessible to the `maven-build` and `buildah` tasks as well.

**Note**: The PVC is in `ReadWriteOnce` mode, meaning only one Pod can use it at a time. This is why our Tasks run sequentially — parallel execution would require a `ReadWriteMany` PVC.

---

## Pipeline Overview

The `bank-demo-pipeline` full CI/CD pipeline consists of these steps:

```
+-------------+     +--------------+     +------------------------+     +---------------------+
|  git-clone  | --> | maven-build  | --> | build-customer-service | --> | build-account-svc   |
+-------------+     +--------------+     +------------------------+     +---------------------+
                                                                                |
+---------------------+     +---------------------+     +---------------------+ |
| build-frontend-image| <-- | build-frontend-app  | <-- | build-backend       | <--+
+---------------------+     +---------------------+     +---------------------+ |
                                                                                |
                                                         +---------------------+ |
                                                         | build-txn-service   | <--+
                                                         +---------------------+
        |
        v
  +----------+
  |  deploy  |
  +----------+
```

**Important**: The buildah tasks run **sequentially** (not in parallel), because the Developer Sandbox memory limit does not allow multiple parallel builds. The `runAfter` chaining ensures the order.

## Pipeline Parameters

```yaml
params:
  - name: git-url
    default: "https://github.com/tamicsko/quarkus-demo.git"
  - name: git-revision
    default: "main"
  - name: image-registry
    default: "image-registry.openshift-image-registry.svc:5000/tamicsko-dev"
```

- **git-url**: The GitHub repo URL. The TriggerBinding overrides it with the value from the webhook.
- **git-revision**: The branch or commit SHA. On push, the `$(body.after)` commit hash goes here.
- **image-registry**: The OpenShift internal registry URL. This does not change, so it's a fixed value.

---

## Task Detailed Explanations

### 1. git-clone (Shared ClusterTask)

**Purpose**: Clone the GitHub repo to the shared workspace.

**Image**: The ClusterTask from the `openshift-pipelines` namespace uses its own image (Alpine + Git).

**Why a shared Task?** `git-clone` is a general operation provided by the OpenShift Pipelines Operator. There is no point writing our own — this is the established pattern.

```yaml
taskRef:
  resolver: cluster
  params:
    - name: kind
      value: task
    - name: name
      value: git-clone
    - name: namespace
      value: openshift-pipelines
params:
  - name: URL
    value: $(params.git-url)
  - name: REVISION
    value: $(params.git-revision)
  - name: DELETE_EXISTING
    value: "true"    # Delete existing content (if left over from previous run on the PVC)
```

### 2. maven-build (Custom maven-java21 Task)

**Purpose**: Compile and package all Java modules (customer-service, account-service, transaction-service, backend) into JAR files.

**Image**: `registry.access.redhat.com/ubi9/openjdk-21:1.20`

#### WHY a Custom Task?

The `maven` ClusterTask provided by the OpenShift Pipelines Operator only contains **JDK 17**. Our project uses **Java 21** (Quarkus 3.34.1 supports and recommends it). Therefore we had to write a custom Task that uses the `ubi9/openjdk-21` image.

#### The "Fake unzip" Hack

This is the most interesting part of this Task. Let's look at the code:

```bash
mkdir -p /tmp/bin
echo "IyEvYmluL2Jhc2gKWklQRklMRT0..." | base64 -d > /tmp/bin/unzip
chmod +x /tmp/bin/unzip
export PATH="/tmp/bin:$PATH"
```

**What happens here?** The base64-encoded content is a shell script that uses `jar xf` instead of `unzip`:

```bash
#!/bin/bash
ZIPFILE=""
DESTDIR=""
while [ $# -gt 0 ]; do
  case "$1" in
    -q) shift ;;
    -d) DESTDIR="$2"; shift 2 ;;
    *)  ZIPFILE="$1"; shift ;;
  esac
done
if [ -z "$ZIPFILE" ]; then echo "unzip error"; exit 1; fi
ABSPATH="$(cd "$(dirname "$ZIPFILE")" && pwd)/$(basename "$ZIPFILE")"
if [ -n "$DESTDIR" ]; then
  mkdir -p "$DESTDIR"
  cd "$DESTDIR"
fi
jar xf "$ABSPATH"
chmod -R +x */bin/ 2>/dev/null || true
find . -name "*.sh" -exec chmod +x {} + 2>/dev/null || true
```

**WHY is this needed?**

The Maven Wrapper (`mvnw`) contains a `.mvn/wrapper/maven-wrapper.jar` file that downloads and extracts Maven. For extraction it uses `unzip`. **But the UBI9 minimal images have neither `unzip` nor `gzip`!** And they cannot be installed because we have no `dnf`/`yum` permissions in the Tekton Pod.

Solution: a "fake" `unzip` command that actually uses the Java `jar xf` command (which can also extract ZIP files and is part of the JDK, so it's always available).

#### Permission Fixes

```bash
# Fix permissions on cached maven (jar xf doesn't preserve execute bits)
find "${MAVEN_USER_HOME}" -path "*/bin/*" -type f -exec chmod +x {} + 2>/dev/null || true
```

**WHY?** `jar xf` (the fake unzip) does not preserve file execute bits. So the Maven `bin/mvn` file will not be executable. Therefore we explicitly set +x on all executable files.

#### Maven Cache on the Workspace

```yaml
env:
  - name: MAVEN_USER_HOME
    value: "$(workspaces.source.path)/.maven"
```

The Maven repository is stored on the workspace (`.maven` directory). This way the next pipeline run does not need to re-download all dependencies — they persist on the PVC.

### 3a-3d. Buildah Image Build Tasks (Shared ClusterTask)

**Purpose**: Build Docker images for each service and push to the internal registry.

**Image**: The `buildah` ClusterTask uses its own image (Red Hat UBI + Buildah).

The four Java services build sequentially:

```
build-customer-service -> build-account-service -> build-transaction-service -> build-backend
```

Every task's parameterization follows the same pattern:

```yaml
params:
  - name: IMAGE
    value: "$(params.image-registry)/customer-service:$(params.git-revision)"
  - name: DOCKERFILE
    value: "./customer-service/src/main/docker/Dockerfile.jvm"
  - name: CONTEXT
    value: "./customer-service"
  - name: TLS_VERIFY
    value: "false"  # No TLS verification needed for internal registry
```

**Why `TLS_VERIFY: false`?** The OpenShift internal registry (`image-registry.openshift-image-registry.svc:5000`) uses a self-signed certificate. Buildah would reject it by default — the `false` setting bypasses this.

### 3e. build-frontend-app (Custom frontend-angular-build Task)

**Purpose**: Produce the Angular frontend's production build.

**Image**: `registry.access.redhat.com/ubi9/nodejs-22:1`

#### WHY a Separate Task?

This is one of the most important architectural decisions in the pipeline. The original plan was a multi-stage Dockerfile to build the frontend:

```dockerfile
# This was the original plan (Dockerfile):
FROM ubi9/nodejs-22:1 AS build
RUN npm ci && npx ng build --configuration production

FROM ubi9/nginx-124:1
COPY --from=build /app/dist/frontend/browser /opt/app-root/src
```

**The problem**: The `buildah` task runs container builds inside a Pod. A multi-stage build means the Node.js build also runs inside the buildah container. This causes two problems:

1. **Memory**: The Node.js build (Angular compiler, TypeScript, webpack/esbuild) needs approximately 500-800 MB RAM. Buildah itself also uses ~200 MB. Combined, this exceeds the 1 Gi container limit.
2. **Overhead**: Buildah runs the inner build through a FUSE overlay filesystem, which is slower.

**Solution**: Two-stage approach:
1. **frontend-angular-build Task**: Node.js runs directly (not inside buildah), getting the full 1 Gi of memory. Output goes to the workspace `frontend/dist/` directory.
2. **build-frontend-image Task**: Only the `Dockerfile.nginx` is built with buildah, which is just two `COPY` operations — done in seconds.

```yaml
# Node.js build memory optimization:
env:
  - name: NODE_OPTIONS
    value: "--max-old-space-size=768"  # Max 768 MB heap for Node.js
  - name: npm_config_cache
    value: "$(workspaces.source.path)/.npm-cache"  # npm cache on the PVC
```

### 3f. build-frontend-image (Buildah + Dockerfile.nginx)

**Purpose**: Package the already-compiled Angular static files into an nginx image.

The `Dockerfile.nginx` is extremely simple:

```dockerfile
FROM registry.access.redhat.com/ubi9/nginx-124:1
COPY dist/frontend/browser /opt/app-root/src
COPY nginx.conf /opt/app-root/etc/nginx.default.d/app.conf
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
```

**Why exactly this way?**
- We use the Red Hat UBI nginx image (not from Docker Hub) — no rate limit
- `/opt/app-root/src` is the default webroot for the UBI nginx
- `nginx.conf` contains the Angular routing (`try_files`) and API proxy config
- The `CMD` overrides the UBI nginx S2I entrypoint (see "Solved Problems" section)
- The port is 8080 (not 80) — OpenShift does not allow root ports

### Dockerfile.nginx vs. Multi-stage Dockerfile

The project has two Dockerfiles for the frontend:

**Dockerfile (multi-stage, for local builds):**
```dockerfile
FROM registry.access.redhat.com/ubi9/nodejs-22:1 AS build
ENV NODE_OPTIONS="--max-old-space-size=384"
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

FROM registry.access.redhat.com/ubi9/nginx-124:1
COPY --from=build /app/dist/frontend/browser /opt/app-root/src
COPY nginx.conf /opt/app-root/etc/nginx.default.d/app.conf
EXPOSE 8080
```
This is for local development (`docker build`), where there is no memory restriction.

**Dockerfile.nginx (for pipeline, minimal):**
```dockerfile
FROM registry.access.redhat.com/ubi9/nginx-124:1
COPY dist/frontend/browser /opt/app-root/src
COPY nginx.conf /opt/app-root/etc/nginx.default.d/app.conf
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
```
This is the version used in the pipeline. Assumption: the `dist/frontend/browser/` directory already exists (prepared by the `frontend-angular-build` Task).

### 4. deploy (openshift-client ClusterTask)

**Purpose**: Deploy the new images and refresh the running Pods.

```bash
# 1. Image tagging: from commit SHA to 'latest'
oc tag tamicsko-dev/customer-service:$(params.git-revision) tamicsko-dev/customer-service:latest
oc tag tamicsko-dev/account-service:$(params.git-revision) tamicsko-dev/account-service:latest
oc tag tamicsko-dev/transaction-service:$(params.git-revision) tamicsko-dev/transaction-service:latest
oc tag tamicsko-dev/backend:$(params.git-revision) tamicsko-dev/backend:latest
oc tag tamicsko-dev/frontend:$(params.git-revision) tamicsko-dev/frontend:latest

# 2. Rolling restart — new Pods with the new images
oc rollout restart deployment/customer-service
oc rollout restart deployment/account-service
oc rollout restart deployment/transaction-service
oc rollout restart deployment/backend
oc rollout restart deployment/frontend

# 3. Wait for completion (max 180 seconds)
oc rollout status deployment/customer-service --timeout=180s
# ... etc.
```

---

## RBAC Configuration

The pipeline execution requires two RoleBindings:

```yaml
# 1. 'edit' ClusterRole — deployment restart, config modification
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: pipeline-edit
roleRef:
  kind: ClusterRole
  name: edit
subjects:
  - kind: ServiceAccount
    name: pipeline-sa

# 2. 'system:image-builder' — push to internal registry
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: pipeline-image-builder
roleRef:
  kind: ClusterRole
  name: system:image-builder
subjects:
  - kind: ServiceAccount
    name: pipeline-sa
```

**Why two separate RoleBindings?**
- The `edit` ClusterRole grants rights to manage Deployments, Services, and other resources
- `system:image-builder` is specifically for pushing to the internal image registry
- The two together cover the pipeline's complete requirements

---

## GitHub Webhook Trigger Flow

### The Components

```
03-triggers.yaml contents:
+-------------------+     +------------------+     +-------------------+
| TriggerBinding    |     | TriggerTemplate  |     | EventListener     |
| (payload parser)  |     | (PipelineRun     |     | (HTTP endpoint)   |
|                   |     |  template)       |     |                   |
| git-revision =    |     | generateName:    |     | triggers:         |
|   body.after      | <-- |   bank-demo-run- | <-- |   - github-push   |
| git-url =         |     | PVC: pipeline-   |     | interceptors:     |
|   body.repo.url   |     |   workspace      |     |   - push events   |
+-------------------+     +------------------+     +-------------------+
                                                            |
                                                   +--------+--------+
                                                   | Route           |
                                                   | github-webhook  |
                                                   | (TLS edge)      |
                                                   +-----------------+
```

The Route provides HTTPS (edge TLS termination) and routes to the `el-github-listener` Service.

### Detailed Webhook Trigger Flow

The complete flow when you push to GitHub:

```
GitHub push event
    |
    v
GitHub Webhook (HTTPS POST)
    |
    v
OpenShift Route (github-webhook)
    |
    v
EventListener Pod (el-github-listener)
    |
    v
Interceptor (filter: only "push" events)
    |
    v
TriggerBinding (extracts: git-url, git-revision from payload)
    |
    v
TriggerTemplate (fills in the PipelineRun template)
    |
    v
PipelineRun created (bank-demo-run-xxxxx)
    |
    v
Pipeline execution (git-clone -> maven -> buildah -> deploy)
```

### EventListener

An HTTP endpoint that waits for external events (e.g. GitHub webhook). When it receives an event, it starts a PipelineRun based on the specified Trigger.

```yaml
apiVersion: triggers.tekton.dev/v1beta1
kind: EventListener
metadata:
  name: github-listener
spec:
  serviceAccountName: pipeline
  triggers:
    - name: github-push
      bindings:
        - ref: github-push-binding
      template:
        ref: github-push-template
      interceptors:
        - ref:
            name: "github"
          params:
            - name: "eventTypes"
              value: ["push"]
```

### TriggerBinding

Extracts necessary parameters from the incoming webhook payload.

```yaml
apiVersion: triggers.tekton.dev/v1beta1
kind: TriggerBinding
metadata:
  name: github-push-binding
spec:
  params:
    - name: git-revision
      value: $(body.after)          # The commit SHA after the push
    - name: git-url
      value: $(body.repository.clone_url)  # The repo URL
```

### TriggerTemplate

The PipelineRun template — filled with the parameters extracted by the TriggerBinding, it creates the PipelineRun.

```yaml
apiVersion: triggers.tekton.dev/v1beta1
kind: TriggerTemplate
metadata:
  name: github-push-template
spec:
  params:
    - name: git-revision
    - name: git-url
  resourcetemplates:
    - apiVersion: tekton.dev/v1
      kind: PipelineRun
      metadata:
        generateName: bank-demo-run-
      spec:
        pipelineRef:
          name: bank-demo-pipeline
        params:
          - name: git-url
            value: $(tt.params.git-url)
          - name: git-revision
            value: $(tt.params.git-revision)
        workspaces:
          - name: shared-workspace
            persistentVolumeClaim:
              claimName: pipeline-workspace
```

### Setup Step by Step

1. **Create EventListener**: OpenShift creates a Service (`el-github-listener`) and a Pod
2. **Create Route**: Public HTTPS URL for the webhook
3. **Configure GitHub Webhook**: On the GitHub repo Settings > Webhooks page, enter the Route URL
4. **Push**: On every push, GitHub sends the webhook

```bash
# Get the EventListener URL
oc get route github-webhook -o jsonpath='{.spec.host}'
# Result e.g.: github-webhook-tamicsko-dev.apps.sandbox-m4.1530.p1.openshiftapps.com
```

### GitHub Webhook Setup

1. GitHub repo > Settings > Webhooks > Add webhook
2. Payload URL: `https://github-webhook-tamicsko-dev.apps.sandbox-m4.1530.p1.openshiftapps.com`
3. Content type: `application/json`
4. Events: Just the push event
5. Secret: (optional, but recommended in production environments)

---

## Shared Tasks from the `openshift-pipelines` Namespace

The OpenShift Pipelines Operator provides numerous pre-built Tasks that any project can use via the `resolver: cluster` reference:

| Task name | Function | Our usage |
|-----------|----------|-----------|
| **git-clone** | Clone Git repo | Yes, first step |
| **maven** | Maven build (JDK 17) | NO — JDK 21 needed, so we wrote a custom one |
| **buildah** | Container image build (rootless) | Yes, for every service image |
| **openshift-client** | Run `oc` commands | Yes, in the deploy step |
| **s2i-java** | Source-to-Image Java build | NOT used |
| **tkn** | Tekton CLI commands | NOT used |

The reference method:
```yaml
taskRef:
  resolver: cluster
  params:
    - name: kind
      value: task
    - name: name
      value: git-clone
    - name: namespace
      value: openshift-pipelines
```

---

## Solved Problems

### 1. Java Version Problem

**Problem**: The OpenShift Pipelines shared `maven` Task only contains JDK 17. Our project uses Java 21.

**Solution**: Created a custom `maven-java21` Task from the `ubi9/openjdk-21:1.20` image.

### 2. Missing gzip/unzip

**Problem**: The UBI9 openjdk image has no `unzip` command. The Maven Wrapper (`mvnw`) tries to extract the downloaded Maven with `unzip` and fails with an error.

**Constraint**: No permission to install packages (no root, no dnf).

**Solution**: A base64-encoded shell script placed at `/tmp/bin/unzip` that actually uses the `jar xf` command (part of the JDK, always available). This also handles ZIP files, so it is transparent to the Maven Wrapper.

### 3. Permission denied (File Permissions)

**Problem**: `jar xf` (the fake unzip) does not preserve file execute bits. Maven `bin/mvn` and other scripts are not executable.

**Solution**: Explicit `chmod +x` after extraction:
```bash
find "${MAVEN_USER_HOME}" -path "*/bin/*" -type f -exec chmod +x {} + 2>/dev/null || true
```

### 4. Docker Hub Rate Limit

**Problem**: Docker Hub anonymous pull limit is 100 pulls / 6 hours. With multiple pipeline runs we hit the limit and builds stopped.

**Solution**: We use Red Hat UBI images everywhere (`registry.access.redhat.com/ubi9/...`). These are:
- Free
- Not subject to rate limits
- Optimized for OpenShift (rootless, security context)

### 5. UBI nginx S2I Entrypoint

**Problem**: The Red Hat UBI nginx image uses the S2I (Source-to-Image) entrypoint by default, which is a complex script and does not start properly in a pure Dockerfile build.

**Solution**: Explicit `CMD ["nginx", "-g", "daemon off;"]` in the Dockerfile, which overrides the S2I entrypoint.

### 6. Memory Limits (1 Gi)

**Problem**: The Developer Sandbox LimitRange gives 750 Mi memory per container by default, with a maximum of 1 Gi. The multi-stage Docker build (Node.js + buildah together) exceeded this.

**Solution**: Two-stage frontend build approach:
1. Separate Tekton Task for the Node.js build (gets the full 1 Gi memory)
2. Separate buildah Task for the nginx image (minimal memory)

### 7. ReplicaSet Quota Exhaustion

**Problem**: The Developer Sandbox allows 100 ReplicaSets per namespace. Every `rollout restart` creates a new ReplicaSet, and old ones remain (with 0 replicas). Over time we reached the 100 limit and deploy stopped.

**Solution**: Periodic cleanup:
```bash
# Delete 0-replica ReplicaSets
oc get rs -o json | jq -r '.items[] | select(.spec.replicas==0) | .metadata.name' | xargs oc delete rs
```

The Deployment's `revisionHistoryLimit` setting can also reduce this:
```yaml
spec:
  revisionHistoryLimit: 5  # Keep only 5 previous versions
```

---

## Troubleshooting Guide

```bash
# List PipelineRuns
tkn pipelinerun list

# View PipelineRun logs
tkn pipelinerun logs bank-demo-run-xxxxx

# Failed TaskRun details
tkn taskrun describe bank-demo-run-xxxxx-maven-build

# EventListener logs (webhook debug)
oc logs deployment/el-github-listener

# Restart pipeline manually
tkn pipeline start bank-demo-pipeline \
  --workspace name=shared-workspace,claimName=pipeline-workspace \
  --use-param-defaults
```

### Pipeline fails at maven-build
- Check if the PVC has enough space: `oc get pvc`
- Check Maven logs for dependency download failures
- If the fake unzip script fails, verify the base64 content is intact

### Pipeline fails at buildah step
- Check if the Dockerfile path is correct
- Check memory usage — buildah may be OOM-killed
- Verify the internal registry is accessible: `oc get svc -n openshift-image-registry`

### Webhook does not trigger
- Check the EventListener pod: `oc logs deployment/el-github-listener`
- Verify the Route is accessible: `curl -I https://<route-url>`
- Check GitHub webhook delivery history in the repo Settings
- Ensure the event type is `push` (not `ping`)

### Deploy step fails
- Check if the image tag exists: `oc get is <service-name>`
- Check if the Deployment exists: `oc get deployment <service-name>`
- Check the pipeline ServiceAccount has correct RBAC: `oc get rolebinding`

---

## Possible Improvements

### 1. Maven Cache with Dedicated PVC

Currently the Maven cache is on the shared workspace PVC. If the PVC is deleted, the cache is lost. Better solution: separate PVC for the cache.

```yaml
workspaces:
  - name: shared-workspace   # Source code
  - name: maven-cache        # Maven dependencies (separate PVC)
```

### 2. Parallel Builds

If more resources were available (not Sandbox), the buildah tasks could run in parallel:

```yaml
- name: build-customer-service
  runAfter: [maven-build]  # All after maven, not after each other
- name: build-account-service
  runAfter: [maven-build]
- name: build-transaction-service
  runAfter: [maven-build]
- name: build-backend
  runAfter: [maven-build]
```

This would require a `ReadWriteMany` PVC (e.g. NFS or CephFS).

### 3. Test Integration

Currently Maven runs with `-DskipTests`. In a production pipeline:

```yaml
- name: unit-tests
  runAfter: [maven-build]
  # mvn test
- name: integration-tests
  runAfter: [unit-tests]
  # mvn verify (Testcontainers + PostgreSQL)
- name: build-images
  runAfter: [integration-tests]
```

### 4. Image Vulnerability Scan

After Buildah, it would be worth running an image vulnerability scan (e.g. Trivy, Clair, or ACS — Advanced Cluster Security):

```yaml
- name: image-scan
  runAfter: [build-backend]
  # trivy image --severity HIGH,CRITICAL ...
```

### 5. Webhook Secret Validation

Currently the GitHub webhook has no secret validation. In production:

```yaml
interceptors:
  - ref:
      name: "github"
    params:
      - name: "secretRef"
        value:
          secretName: github-webhook-secret
          secretKey: token
```

### 6. Notifications

Pipeline success/failure notifications (Slack, email, Teams):

```yaml
finally:
  - name: notify
    taskRef:
      name: send-notification
    params:
      - name: status
        value: $(tasks.deploy.status)
```

## Further Reading

- [OpenShift Concepts](concepts.md)
- [Sandbox Setup](sandbox-setup.md)
