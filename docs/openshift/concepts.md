# OpenShift Overview and Concepts

## What Is OpenShift?

OpenShift is an enterprise Kubernetes platform developed by Red Hat. It is essentially "Kubernetes++": it builds on vanilla Kubernetes but adds numerous extra features, security settings, and developer tools.

### OpenShift vs. Vanilla Kubernetes

| Feature | Vanilla Kubernetes | OpenShift |
|---------|-------------------|-----------|
| **Installation** | Manual, lots of configuration | Automated (IPI/UPI installer) |
| **Web console** | None (only Dashboard addon) | Full-featured Developer + Admin console |
| **Routing** | Ingress Controller needed separately | Built-in Route object (HAProxy) |
| **Image build** | No built-in support | BuildConfig, S2I (Source-to-Image) |
| **CI/CD** | Must be installed separately | Tekton Pipelines Operator built-in |
| **Registry** | Must be installed separately | Built-in internal image registry |
| **Security** | More permissive by default | SCC (Security Context Constraints), root disabled |
| **CLI** | `kubectl` | `oc` (kubectl-compatible + extra features) |
| **Monitoring** | Prometheus separately | Built-in Prometheus + Grafana stack |
| **Logging** | EFK separately | Built-in OpenShift Logging (Loki/Elasticsearch) |

### Important Difference: Security

OpenShift by default **prohibits running as root user**. This means:
- Containers cannot run as root (UID 0)
- Every container receives a random UID (e.g. 1000640000)
- Only ports above 1024 can be used
- File system permissions require special attention

This design decision influenced the Docker image choices in our project: the Red Hat UBI (Universal Base Image) images are optimized exactly for this purpose.

---

## Core Concepts

### Pod

The smallest deployable unit in Kubernetes/OpenShift. A Pod contains one or more containers that share a common network namespace and storage.

```yaml
# A typical pod (in practice, created from Deployments)
apiVersion: v1
kind: Pod
metadata:
  name: backend-pod
spec:
  containers:
    - name: backend
      image: image-registry.openshift-image-registry.svc:5000/tamicsko-dev/backend:latest
      ports:
        - containerPort: 8080
```

**Important**: Pods are never created directly — they are always managed through Deployments.

### Deployment

A Deployment describes how to create and maintain Pods. It manages the replica count, rolling update strategy, and rollback capability.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
spec:
  replicas: 1
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
        - name: backend
          image: image-registry.openshift-image-registry.svc:5000/tamicsko-dev/backend:latest
          ports:
            - containerPort: 8080
          resources:
            limits:
              memory: 512Mi
              cpu: 500m
```

### Service

A Service is a stable network endpoint in front of Pods. Pod IPs change (e.g. on restart), but a Service's IP and DNS name remain constantly reachable.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: backend
spec:
  selector:
    app: backend
  ports:
    - port: 8080
      targetPort: 8080
```

The Service name is resolvable via DNS within the cluster: `backend.tamicsko-dev.svc.cluster.local` or simply `backend` (if we are in the same namespace).

### Route (OpenShift-specific)

A Route makes a Service accessible from outside the cluster. This is OpenShift's answer to Kubernetes Ingress, but simpler and more capable.

```yaml
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: frontend
spec:
  to:
    kind: Service
    name: frontend
  port:
    targetPort: 8080
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
```

#### Route vs. Kubernetes Ingress

| Feature | Route | Ingress |
|---------|-------|---------|
| **Built-in** | Yes (OpenShift) | Ingress Controller needed |
| **TLS** | Automatic edge/passthrough/reencrypt | Must be configured separately |
| **Wildcard DNS** | Automatic (`*.apps.cluster.example.com`) | Manual DNS setup |
| **CLI** | `oc expose svc/frontend` | Write YAML |
| **HA** | Built-in HAProxy | Depends on the Ingress Controller |

The Route automatically receives a URL from the cluster wildcard domain, e.g.:
`frontend-tamicsko-dev.apps.sandbox-m4.1530.p1.openshiftapps.com`

### StatefulSet

For stateless applications we use Deployments. For stateful applications — like databases — a StatefulSet is needed because:
- Guaranteed, stable network identity (pod-0, pod-1, ...)
- Stable, dedicated PVC for each replica
- Ordered startup/shutdown

In our project, the PostgreSQL database runs as a StatefulSet.

### PersistentVolumeClaim (PVC)

A PVC is a storage request: the Pod does not directly request the disk, but submits a "claim," and the cluster fulfills it with a PersistentVolume.

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: pipeline-workspace
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
```

In our pipeline, the `pipeline-workspace` PVC stores the Git repo and Maven cache between tasks.

### ConfigMap

Storage of configuration data in key-value pairs. The container can mount it as environment variables or as a file.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  DATABASE_HOST: "postgresql"
  LOG_LEVEL: "INFO"
```

### Secret

Storage of sensitive data (passwords, tokens). Similar to ConfigMap but stored base64-encoded and managed more strictly by RBAC.

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-credentials
type: Opaque
data:
  username: YWRtaW4=      # base64("admin")
  password: cGFzc3dvcmQ=  # base64("password")
```

### ImageStream (OpenShift-specific)

An ImageStream is an abstraction over container images. It can track different image versions (tags) and automatically trigger Deployment restarts when a new image arrives.

```bash
# List an ImageStream's tags
oc get is backend -o yaml
```

In our project, ImageStreams receive new tags in the pipeline deploy step:
```bash
oc tag tamicsko-dev/backend:main tamicsko-dev/backend:latest
```

This means: "mark the image built from the `main` commit as `latest` too." The Deployment uses this `latest` tag.

#### How ImageStreams Work in Detail

An ImageStream is not the image itself — it is a **pointer** that points to image tags. Advantages:

1. **Automatic trigger**: When a new image arrives on a tag, the ImageStream trigger restarts the Deployment.
2. **Abstraction**: The Deployment references the ImageStream name, not the full registry URL.
3. **History**: We can preserve the previous image version — simpler rollback.

Example from our pipeline:
```bash
# The pipeline uploads the fresh image with the 'main' tag
# Then re-tags it to 'latest':
oc tag tamicsko-dev/backend:main tamicsko-dev/backend:latest
```

The Deployment uses the `image-registry.openshift-image-registry.svc:5000/tamicsko-dev/backend:latest` image, and `rollout restart` refreshes the running Pods.

### BuildConfig (OpenShift-specific)

A BuildConfig describes how to build an image. Several strategies exist:
- **Docker**: Dockerfile-based build
- **S2I (Source-to-Image)**: Automatic image building from source code
- **Custom**: Custom build process

In our project we do NOT use BuildConfig for the pipeline builds — instead we build images in the Tekton Pipeline using the Buildah task. This is a more flexible and portable solution. However, we do use BuildConfig for **binary builds** (manually uploading JARs from the local machine).

---

## How Routes Work in Detail

OpenShift Routes are based on HAProxy and support three TLS modes:

### 1. Edge Termination
TLS terminates at the Route (HAProxy), then unencrypted HTTP goes to the Service.
```yaml
tls:
  termination: edge
```
In our project this is the pattern: the Route accepts HTTPS and forwards HTTP to the Pods.

### 2. Passthrough
TLS goes directly to the Pod — the HAProxy does not decrypt it. Useful when the application handles TLS itself.
```yaml
tls:
  termination: passthrough
```

### 3. Re-encrypt
TLS terminates at the Route, then is re-encrypted going to the Pod. Double encryption, but every hop is protected.
```yaml
tls:
  termination: reencrypt
  destinationCACertificate: |
    -----BEGIN CERTIFICATE-----
    ...
```

---

## Security Context Constraints (SCC)

OpenShift SCCs control what a pod is allowed to do. The default `restricted` SCC:
- Denies running as root
- Assigns a random UID from a namespace-specific range
- Denies host network/port/PID access
- Enforces read-only root filesystem (when configured)

This is why standard Docker Hub images (e.g. `nginx:alpine`) often fail on OpenShift — they expect to run as root. We use Red Hat UBI images that are designed for rootless execution.

---

## Resource Quotas and LimitRange

### ResourceQuota

ResourceQuota is a namespace-level restriction — how many total resources the given project can use.

Typical limits in the Developer Sandbox:
- **CPU**: 20 cores (request), 20 cores (limit)
- **Memory**: 18 Gi (request), 18 Gi (limit)
- **Pods**: 100
- **ReplicaSets**: 100 (this caused problems in our pipeline!)
- **PVC**: 5 items, total 15 Gi
- **Services**: 25

```bash
# View quota
oc describe quota

# View resource usage
oc describe quota -o yaml
```

### LimitRange

LimitRange is a pod/container-level restriction — how many resources a container gets by default and at maximum.

Typical LimitRange in the Developer Sandbox:
- **Default memory limit**: 750Mi (per container!)
- **Default CPU limit**: 500m
- **Max memory**: 1Gi

```bash
# View LimitRange
oc describe limitrange
```

**Important**: The 1 Gi memory limit restricted the frontend build and the Maven build in our project. This is why we had to separate the frontend build into a dedicated Tekton Task (see: [Pipeline Details](pipeline.md)).

---

## S2I vs Docker Build Strategy

| Feature | S2I (Source-to-Image) | Docker Build |
|---------|----------------------|-------------|
| **Input** | Source code | Dockerfile + context |
| **How it works** | Builder image compiles + packages automatically | Runs Dockerfile instructions step by step |
| **Dockerfile needed** | No | Yes |
| **Customization** | Limited (assemble/run scripts) | Full control |
| **Use case** | Quick start, standard apps | Custom builds, complex dependencies |
| **Our choice** | Not used | Used (Buildah in pipeline, Binary Build for manual) |

We chose the Docker build strategy because it gives full control over the build process and is more portable across different CI/CD systems.

---

## Essential `oc` Commands

### Project (namespace) management

```bash
# View current project
oc project

# Switch project
oc project tamicsko-dev

# List all available projects
oc projects

# Create new project (if you have permissions)
oc new-project my-project
```

### Pod operations

```bash
# List pods
oc get pods

# Detailed pod listing (IP, node, status)
oc get pods -o wide

# Details of a specific pod
oc describe pod backend-6f8c9d7b5f-xk2rv

# Pod logs
oc logs backend-6f8c9d7b5f-xk2rv

# Follow pod logs (tail -f)
oc logs -f backend-6f8c9d7b5f-xk2rv

# Previous (crashed) pod logs
oc logs backend-6f8c9d7b5f-xk2rv --previous

# Open shell in a running pod
oc rsh backend-6f8c9d7b5f-xk2rv

# Run command in pod
oc exec backend-6f8c9d7b5f-xk2rv -- curl localhost:8080/q/health

# Copy file from pod
oc cp backend-6f8c9d7b5f-xk2rv:/tmp/log.txt ./log.txt
```

### Debugging / troubleshooting

```bash
# Pod events (why it won't start)
oc describe pod <pod-name>

# Deployment events
oc describe deployment backend

# All events in the namespace
oc get events --sort-by='.lastTimestamp'

# Resource usage
oc adm top pods

# Port forward (for local testing)
oc port-forward svc/backend 8080:8080

# Start debug pod (same image but /bin/sh entry point)
oc debug deployment/backend
```

### Deployment and scaling

```bash
# List deployments
oc get deployments

# Restart deployment (rolling restart)
oc rollout restart deployment/backend

# View rollout status
oc rollout status deployment/backend

# Roll back to previous version
oc rollout undo deployment/backend

# Modify replica count
oc scale deployment/backend --replicas=2

# Set up autoscaling
oc autoscale deployment/backend --min=1 --max=3 --cpu-percent=80
```

### Network (Service, Route)

```bash
# List services
oc get svc

# List routes
oc get routes

# Expose service as Route (create public URL)
oc expose svc/frontend

# Get route URL
oc get route frontend -o jsonpath='{.spec.host}'

# Create HTTPS route
oc create route edge frontend --service=frontend --port=8080
```

### Image and build

```bash
# List ImageStreams
oc get is

# Tag an image
oc tag tamicsko-dev/backend:main tamicsko-dev/backend:latest

# Push to internal registry
# (this is automatic in the pipeline, but for manual build:)
oc registry login
docker push image-registry.openshift-image-registry.svc:5000/tamicsko-dev/backend:latest

# Build logs (if using BuildConfig)
oc logs -f bc/backend
```

### Resource management

```bash
# List all resources
oc get all

# Export YAML (backup / debug)
oc get deployment backend -o yaml

# Delete a resource
oc delete pod backend-6f8c9d7b5f-xk2rv

# Apply YAML
oc apply -f deployment.yaml

# Delete resources based on YAML
oc delete -f deployment.yaml
```

---

## Developer Sandbox Tips

The Red Hat Developer Sandbox is a free, 30-day OpenShift environment that:
- Provides limited resources (see above)
- Does not allow creating ClusterRoles
- Does not allow installing custom Operators
- "Hibernates" the project after 12 hours of inactivity
- Does not support `NodePort` type Services

### Practical Tips

1. **Hibernation handling**: After 12 hours of inactivity, Pods stop. Logging into the web console restarts everything. Tip: use a cron job with the `oc whoami` command to keep it active.

2. **Memory limit management**: 750Mi is the default container memory limit. If the Java application (Quarkus) needs more memory, an explicit `resources.limits.memory` is needed in the Deployment.

3. **ReplicaSet quota**: 100 ReplicaSets is the limit. Every `rollout restart` creates a new ReplicaSet. Periodically clean up the old ones:
   ```bash
   # List the 0-replica (old) ReplicaSets
   oc get rs --sort-by='.metadata.creationTimestamp'
   ```

4. **PVC usage**: 5 PVCs is the limit. The pipeline workspace, database, and other needs must fit within this.

5. **Avoiding Docker Hub rate limit**: Red Hat UBI images come from `registry.access.redhat.com` — these are not subject to Docker Hub rate limits. This is why we use UBI images everywhere in our project.

6. **Log viewing for debugging**:
   ```bash
   # All pod logs at once (if there are multiple pods)
   oc logs -l app=backend --all-containers

   # Pipeline Run logs
   tkn pipelinerun logs bank-demo-run-xxxxx
   ```

7. **Using the web console**: In the OpenShift web console Developer view, you can see the Topology view that visually shows the connections between your applications. This is very useful for demo presentations.

## Further Reading

- [Pipeline Details](pipeline.md)
- [Sandbox Setup](sandbox-setup.md)
