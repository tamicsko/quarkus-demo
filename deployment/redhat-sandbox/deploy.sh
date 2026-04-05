#!/bin/bash
# =============================================================================
# Red Hat Developer Sandbox — Bank Demo deploy script
# =============================================================================
# Használat: bash deployment/redhat-sandbox/deploy.sh
# Előfeltétel: oc login már megtörtént
# =============================================================================

set -e

NAMESPACE="tamicsko-dev"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "============================================"
echo "  Bank Demo → Red Hat Developer Sandbox"
echo "  Namespace: $NAMESPACE"
echo "============================================"

# --- Ellenőrzés ---
echo ""
echo "[1/8] Ellenőrzés: oc login és projekt..."
oc whoami > /dev/null 2>&1 || { echo "HIBA: Nem vagy bejelentkezve! Futtasd: oc login --token=... --server=..."; exit 1; }
oc project "$NAMESPACE" > /dev/null 2>&1 || { echo "HIBA: Nem érhető el a $NAMESPACE projekt!"; exit 1; }
echo "  ✓ Bejelentkezve: $(oc whoami), projekt: $NAMESPACE"

# --- Secrets & ConfigMap ---
echo ""
echo "[2/8] Secrets és ConfigMap alkalmazása..."
oc apply -f "$SCRIPT_DIR/secrets.yaml"
oc apply -f "$SCRIPT_DIR/configmaps.yaml"
echo "  ✓ Secrets és ConfigMap kész"

# --- Postgres ---
echo ""
echo "[3/8] PostgreSQL deploy..."
# A postgres.yaml-ban PGDATA=/var/lib/postgresql/data/pgdata van beállítva
# (OpenShift PVC-n lost+found könyvtár van, Postgres nem tud közvetlenül mountolni)
oc apply -f "$SCRIPT_DIR/postgres.yaml"
echo "  Várakozás a Postgres pod-ra..."
oc rollout status statefulset/postgres --timeout=120s 2>/dev/null || \
  oc wait pod/postgres-0 --for=condition=Ready --timeout=120s
echo "  ✓ PostgreSQL fut"

# --- Java build ---
echo ""
echo "[4/8] Java modulok buildolése (JAR)..."
cd "$PROJECT_ROOT"
./mvnw package -DskipTests -q
echo "  ✓ JAR-ok elkészültek"

# --- BuildConfig-ok létrehozása YAML-ból ---
echo ""
echo "[5/8] OpenShift BuildConfig-ok és ImageStream-ek..."
# Az oc new-build --binary --dockerfile kombináció nem működik megbízhatóan,
# ezért YAML-ból hozzuk létre a BuildConfig-okat (source.type: Binary + source.dockerfile)
oc apply -f "$SCRIPT_DIR/buildconfigs.yaml"
echo "  ✓ BuildConfig-ok és ImageStream-ek kész"

echo ""
echo "[6/8] Image-ek buildolése az OpenShift-en..."
for SVC in customer-service account-service transaction-service backend; do
  echo "  Build: $SVC..."
  oc start-build "$SVC" --from-dir="$PROJECT_ROOT/$SVC/target/quarkus-app" --follow --wait
done

# Frontend — a teljes könyvtárat feltöltjük (van benne Dockerfile)
# A Dockerfile nginxinc/nginx-unprivileged:alpine-t használ (8080-as port)
# mert az OpenShift random UID-vel futtat (nem root)
echo "  Build: frontend..."
oc start-build frontend --from-dir="$PROJECT_ROOT/frontend" --follow --wait
echo "  ✓ Minden image kész"

# --- Deploymentek ---
echo ""
echo "[7/8] Deployment-ek alkalmazása..."
oc apply -f "$SCRIPT_DIR/customer-service.yaml"
oc apply -f "$SCRIPT_DIR/account-service.yaml"
oc apply -f "$SCRIPT_DIR/transaction-service.yaml"
oc apply -f "$SCRIPT_DIR/backend.yaml"
oc apply -f "$SCRIPT_DIR/frontend.yaml"

echo "  Várakozás a pod-okra..."
for DEPLOY in customer-service account-service transaction-service backend frontend; do
  echo -n "    $DEPLOY: "
  oc rollout status deployment/"$DEPLOY" --timeout=180s 2>&1 | tail -1
done
echo "  ✓ Minden deployment fut"

# --- URL-ek ---
echo ""
echo "[8/8] Route URL-ek:"
echo "============================================"
FRONTEND_URL=$(oc get route bank-demo -o jsonpath='{.spec.host}' 2>/dev/null)
BACKEND_URL=$(oc get route backend-api -o jsonpath='{.spec.host}' 2>/dev/null)
echo "  Frontend:    https://$FRONTEND_URL"
echo "  Backend API: https://$BACKEND_URL"
echo "  Swagger UI:  https://$BACKEND_URL/q/swagger-ui"
echo "============================================"
echo ""
echo "Deploy kész!"
