#!/bin/bash

###############################################################################
# WAHA (WhatsApp HTTP API) Installation Script
# Orange Wallet Notification Infrastructure
#
# This script installs WAHA in the orange-wallet namespace
# Prerequisites:
#   - kubectl configured and connected to cluster
#   - Helm 3 installed
#   - orange-wallet namespace exists
###############################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE="orange-wallet"
CHART_PATH="../../../.k8s/charts/infrastructure/waha"
VALUES_FILE="../../../.k8s/values/waha-values.yaml"
RELEASE_NAME="waha"

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   WAHA Installation - Orange Wallet Infrastructure        ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

###############################################################################
# Step 1: Generate Secure Secrets
###############################################################################
echo -e "${YELLOW}[1/7] Generating secure secrets...${NC}"

API_KEY=$(openssl rand -base64 32)
DASHBOARD_PASSWORD=$(openssl rand -base64 24)
WEBHOOK_HMAC_SECRET=$(openssl rand -base64 32)

echo -e "${GREEN}✓ Generated API Key${NC}"
echo -e "${GREEN}✓ Generated Dashboard Password${NC}"
echo -e "${GREEN}✓ Generated Webhook HMAC Secret${NC}"
echo ""

###############################################################################
# Step 2: Create Temporary Values File with Secrets
###############################################################################
echo -e "${YELLOW}[2/7] Creating values file with secrets...${NC}"

TEMP_VALUES="/tmp/waha-values-${RANDOM}.yaml"

cat > "$TEMP_VALUES" <<EOF
# WAHA Production Values - Auto-generated
# Generated: $(date)

name: waha
namespace: ${NAMESPACE}
replicaCount: 1

image:
  repository: devlikeapro/waha
  tag: latest
  pullPolicy: Always

service:
  type: ClusterIP
  port: 3000

secret:
  name: waha-secret
  apiKey: "${API_KEY}"
  dashboardUsername: "admin"
  dashboardPassword: "${DASHBOARD_PASSWORD}"
  webhookHmacSecret: "${WEBHOOK_HMAC_SECRET}"

config:
  timezone: "Asia/Jakarta"
  whatsappEngine: "WEBJS"
  logFormat: "JSON"
  logLevel: "info"
  filesLifetime: 0
  webhookUrl: "http://notification-worker:8080/api/webhooks/waha"
  webhookEvents: "message,message.any,session.status,message.ack"
  autoStartDelay: 5
  restartAllSessions: true
  workerId: "waha-orangewallet-prod"
  printQR: true

persistence:
  sessions:
    enabled: true
    size: 2Gi
    accessMode: ReadWriteOnce
    mountPath: /app/.sessions
  media:
    enabled: true
    size: 5Gi
    accessMode: ReadWriteOnce
    mountPath: /app/.media

resources:
  requests:
    memory: "256Mi"
    cpu: "200m"
  limits:
    memory: "512Mi"
    cpu: "500m"

healthcheck:
  liveness:
    initialDelaySeconds: 40
    periodSeconds: 30
    timeoutSeconds: 10
    failureThreshold: 3
  readiness:
    initialDelaySeconds: 30
    periodSeconds: 10
    timeoutSeconds: 5
    failureThreshold: 3
EOF

echo -e "${GREEN}✓ Values file created: $TEMP_VALUES${NC}"
echo ""

###############################################################################
# Step 3: Verify Namespace Exists
###############################################################################
echo -e "${YELLOW}[3/7] Verifying namespace...${NC}"

if kubectl get namespace ${NAMESPACE} &> /dev/null; then
    echo -e "${GREEN}✓ Namespace '${NAMESPACE}' exists${NC}"
else
    echo -e "${RED}✗ Namespace '${NAMESPACE}' does not exist${NC}"
    echo -e "${YELLOW}Creating namespace...${NC}"
    kubectl create namespace ${NAMESPACE}
    echo -e "${GREEN}✓ Namespace created${NC}"
fi
echo ""

###############################################################################
# Step 4: Check if WAHA is already installed
###############################################################################
echo -e "${YELLOW}[4/7] Checking existing installation...${NC}"

if helm list -n ${NAMESPACE} | grep -q "^${RELEASE_NAME}"; then
    echo -e "${YELLOW}! WAHA is already installed${NC}"
    read -p "Do you want to upgrade? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        ACTION="upgrade"
    else
        echo -e "${RED}Installation cancelled${NC}"
        rm -f "$TEMP_VALUES"
        exit 0
    fi
else
    ACTION="install"
    echo -e "${GREEN}✓ No existing installation found${NC}"
fi
echo ""

###############################################################################
# Step 5: Install/Upgrade WAHA
###############################################################################
echo -e "${YELLOW}[5/7] ${ACTION^}ing WAHA...${NC}"

if [ "$ACTION" = "install" ]; then
    helm install ${RELEASE_NAME} ${CHART_PATH} \
        --namespace ${NAMESPACE} \
        --values "$TEMP_VALUES" \
        --timeout 5m \
        --wait
else
    helm upgrade ${RELEASE_NAME} ${CHART_PATH} \
        --namespace ${NAMESPACE} \
        --values "$TEMP_VALUES" \
        --timeout 5m \
        --wait
fi

echo -e "${GREEN}✓ WAHA ${ACTION}ed successfully!${NC}"
echo ""

###############################################################################
# Step 6: Verify Deployment
###############################################################################
echo -e "${YELLOW}[6/7] Verifying deployment...${NC}"

echo "Waiting for pod to be ready..."
kubectl wait --for=condition=ready pod \
    -l app=waha \
    -n ${NAMESPACE} \
    --timeout=120s

echo -e "${GREEN}✓ WAHA pod is running${NC}"
echo ""

###############################################################################
# Step 7: Display Information
###############################################################################
echo -e "${YELLOW}[7/7] Installation complete!${NC}"
echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║               WAHA Installation Summary                   ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Get pod information
POD_NAME=$(kubectl get pods -n ${NAMESPACE} -l app=waha -o jsonpath='{.items[0].metadata.name}')
POD_STATUS=$(kubectl get pods -n ${NAMESPACE} -l app=waha -o jsonpath='{.items[0].status.phase}')

echo -e "${GREEN}Pod Information:${NC}"
echo "  Name:      ${POD_NAME}"
echo "  Status:    ${POD_STATUS}"
echo "  Namespace: ${NAMESPACE}"
echo ""

echo -e "${GREEN}Connection Details:${NC}"
echo "  Internal URL: http://waha:3000"
echo "  Dashboard:    http://waha:3000/dashboard"
echo ""

echo -e "${GREEN}Credentials:${NC}"
echo "  Username:  admin"
echo "  Password:  ${DASHBOARD_PASSWORD}"
echo "  API Key:   ${API_KEY}"
echo ""

# Save credentials to secure file
CREDS_FILE="$HOME/.waha-credentials"
cat > "$CREDS_FILE" <<EOF
# WAHA Credentials - Orange Wallet
# Generated: $(date)
# KEEP THIS FILE SECURE!

WAHA_API_KEY="${API_KEY}"
WAHA_DASHBOARD_USERNAME="admin"
WAHA_DASHBOARD_PASSWORD="${DASHBOARD_PASSWORD}"
WAHA_WEBHOOK_HMAC_SECRET="${WEBHOOK_HMAC_SECRET}"

# Connection URLs
WAHA_URL="http://waha:3000"
WAHA_DASHBOARD="http://waha:3000/dashboard"
EOF

chmod 600 "$CREDS_FILE"
echo -e "${GREEN}✓ Credentials saved to: ${CREDS_FILE}${NC}"
echo ""

echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}Next Steps:${NC}"
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo "1. Create a WhatsApp session:"
echo "   kubectl exec -it -n ${NAMESPACE} ${POD_NAME} -- wget -qO- http://localhost:3000/api/sessions -X POST -H 'Content-Type: application/json' -d '{\"name\": \"default\"}'"
echo ""
echo "2. Get QR code from logs:"
echo "   kubectl logs -f -n ${NAMESPACE} ${POD_NAME} | grep -A 20 'QR'"
echo ""
echo "3. Scan the QR code with your WhatsApp mobile app"
echo ""
echo "4. Verify session status:"
echo "   kubectl logs -n ${NAMESPACE} ${POD_NAME} | grep -i session"
echo ""
echo -e "${BLUE}Documentation: https://waha.devlike.pro/${NC}"
echo ""

# Cleanup temp files
rm -f "$TEMP_VALUES"

echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║             Installation Completed Successfully!          ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
