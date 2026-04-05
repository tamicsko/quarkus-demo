# Local Development Environment Setup

Windows 11 development environment for the Raiffeisen bank demo project.

## Required Tools

| Tool | Version | Installation |
|---|---|---|
| Java JDK | 21 LTS (Eclipse Temurin) | `winget install EclipseAdoptium.Temurin.21.JDK` |
| Maven | 3.9.x | Manual installation (see below) |
| Node.js | 24 LTS | `winget upgrade OpenJS.NodeJS.LTS` |
| Angular CLI | 19.x | `npm install -g @angular/cli` |
| Git | latest | `winget install Git.Git` |
| Docker Desktop | latest | `winget install Docker.DockerDesktop` |
| Quarkus CLI | latest | Via JBang (see below) |
| oc CLI | latest | Manual installation (see below) |
| jq | 1.7+ | `winget install jqlang.jq` or manual (see below) |

## Installation Steps

### 1. Java 21 LTS

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
java -version
```

**Important:** You must also set the `JAVA_HOME` environment variable to the JDK 21 path (e.g. `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`), otherwise Maven and other Java-based tools will use an older version. Set `JAVA_HOME` in the system environment variables.

`PATH` and `JAVA_HOME` are two different things:
- `PATH` — tells the command line which `java.exe` to find
- `JAVA_HOME` — this is what Java-based tools look at (Maven, Gradle, Tomcat, etc.)

### 2. Maven (Manual Installation)

Maven is not available in winget, so it must be installed manually:

1. Download: https://maven.apache.org/download.cgi -> "Binary zip archive"
2. Extract to: `D:\tools\apache-maven-3.9.14` (or similar path without spaces)
3. Set environment variables:
   - `MAVEN_HOME` = `D:\tools\apache-maven-3.9.14`
   - Add to `Path`: `%MAVEN_HOME%\bin`
4. Verify in a new terminal:

```powershell
mvn -version
```

The Java version in the output must be 21.x.x. If it shows 17, then `JAVA_HOME` is not set correctly.

### 3. Node.js

```powershell
winget upgrade OpenJS.NodeJS.LTS
node -v
```

### 4. Angular CLI

```powershell
npm install -g @angular/cli
ng version
```

You can check the location of globally installed npm packages with `npm prefix -g`.

### 5. Quarkus CLI (via JBang)

The Quarkus CLI is installed through JBang. JBang is a lightweight Java script runner recommended by the Quarkus team for CLI installation.

```powershell
# Install JBang
iex "& { $(iwr -useb https://ps.jbang.dev) } app setup"

# Install Quarkus CLI
jbang app install --fresh --force quarkus@quarkusio

# Verify (in a new terminal)
quarkus version
```

### 6. Docker Desktop

```powershell
winget install Docker.DockerDesktop
docker --version
```

Docker Desktop must have the WSL 2 backend enabled. Use **Git Bash** for running shell scripts (`bank-demo-ctl`).

### 7. oc CLI (OpenShift Client)

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

## Verification Checklist

All of these must work in a new terminal:

```powershell
java -version          # 21.x.x
mvn -version           # 3.9.x, Java 21
node -v                # 24.x.x
ng version             # 19.x
git --version          # 2.x
docker --version       # 29.x
quarkus version        # 3.x
oc version --client    # 4.x
```

### 8. jq (JSON processor — required for seed data)

**Option A — winget:**
```powershell
winget install jqlang.jq
```

**Option B — manual (Git Bash):**
```bash
curl -sL -o /c/tools/jq.exe https://github.com/jqlang/jq/releases/download/jq-1.7.1/jq-win64.exe
# Ensure C:\tools is in PATH
```

**Verify:**
```bash
jq --version    # jq-1.7.1
```

## Next Steps

- [Running the Project](running.md)
