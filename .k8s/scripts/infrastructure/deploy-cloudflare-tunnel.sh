#!/bin/bash
# Deploy Cloudflare Tunnel to Kubernetes
# This script deploys cloudflared DaemonSet to the cluster

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

NAMESPACE="${NAMESPACE:-orange-wallet}"
MANIFESTS_PATH="../../manifests/infrastructure/cloudflare"

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Deploy Cloudflare Tunnel${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Check if namespace exists
if ! kubectl get namespace $NAMESPACE &>/dev/null; then
    echo -e "${RED}Error: Namespace '$NAMESPACE' does not exist${NC}"
    exit 1
fi

echo -e "${BLUE}[1/5] Checking prerequisites...${NC}"

# Check if credentials secret file exists
SECRET_FILE="$MANIFESTS_PATH/cloudflared-secret.yaml"
if [ ! -f "$SECRET_FILE" ]; then
    echo -e "${RED}Error: Credentials file not found: $SECRET_FILE${NC}"
    echo -e "${YELLOW}Run setup-cloudflare-tunnel.sh first!${NC}"
    exit 1
fi

# Verify secret contains actual credentials (not placeholder)
if grep -q "YOUR_TUNNEL_ID" "$SECRET_FILE"; then
    echo -e "${RED}Error: Secret file contains placeholder values${NC}"
    echo -e "${YELLOW}Run setup-cloudflare-tunnel.sh to generate credentials${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Prerequisites OK${NC}"
echo ""

echo -e "${BLUE}[2/5] Deploying Cloudflare Tunnel resources...${NC}"

# Apply manifests using kustomize
if command -v kustomize &> /dev/null; then
    echo -e "${YELLOW}Using kustomize...${NC}"
    kubectl apply -k $MANIFESTS_PATH
else
    echo -e "${YELLOW}Using kubectl apply...${NC}"
    kubectl apply -f $MANIFESTS_PATH/cloudflared-secret.yaml
    kubectl apply -f $MANIFESTS_PATH/cloudflared-config.yaml
    kubectl apply -f $MANIFESTS_PATH/cloudflared-daemonset.yaml
    kubectl apply -f $MANIFESTS_PATH/cloudflared-service.yaml
fi

echo -e "${GREEN}✓ Resources applied${NC}"
echo ""

echo -e "${BLUE}[3/5] Waiting for DaemonSet rollout...${NC}"

# Wait for DaemonSet to be ready
kubectl rollout status daemonset/cloudflared -n $NAMESPACE --timeout=300s

echo -e "${GREEN}✓ DaemonSet ready${NC}"
echo ""

echo -e "${BLUE}[4/5] Verifying deployment...${NC}"

# Get pod status
echo -e "${YELLOW}Pod Status:${NC}"
kubectl get pods -n $NAMESPACE -l app=cloudflared -o wide

echo ""
echo -e "${YELLOW}DaemonSet Status:${NC}"
kubectl get daemonset cloudflared -n $NAMESPACE

echo ""
echo -e "${YELLOW}Service Status:${NC}"
kubectl get svc cloudflared-metrics -n $NAMESPACE

echo ""

echo -e "${BLUE}[5/5] Checking tunnel connectivity...${NC}"

# Wait a bit for tunnel to establish connection
echo -e "${YELLOW}Waiting for tunnel to establish connection (30s)...${NC}"
sleep 30

# Get pod name
POD_NAME=$(kubectl get pods -n $NAMESPACE -l app=cloudflared -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)

if [ -n "$POD_NAME" ]; then
    echo -e "${YELLOW}Recent logs from $POD_NAME:${NC}"
    kubectl logs $POD_NAME -n $NAMESPACE --tail=20

    echo ""
    echo -e "${YELLOW}Checking tunnel status...${NC}"

    # Check if logs contain success indicators
    if kubectl logs $POD_NAME -n $NAMESPACE | grep -q "Registered tunnel connection"; then
        echo -e "${GREEN}✓ Tunnel connection established!${NC}"
    else
        echo -e "${YELLOW}! Tunnel may still be connecting... Check logs for details${NC}"
    fi
else
    echo -e "${RED}No cloudflared pods found${NC}"
fi

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Deployment Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

echo -e "${YELLOW}Tunnel Endpoints:${NC}"
echo "  https://api.orangebybni.my.id"
echo "  https://auth.orangebybni.my.id"
echo "  https://wallet.orangebybni.my.id"
echo ""

echo -e "${YELLOW}Useful Commands:${NC}"
echo -e "  View logs:       ${GREEN}kubectl logs -f daemonset/cloudflared -n $NAMESPACE${NC}"
echo -e "  Get pod status:  ${GREEN}kubectl get pods -n $NAMESPACE -l app=cloudflared${NC}"
echo -e "  View metrics:    ${GREEN}kubectl port-forward svc/cloudflared-metrics 2000:2000 -n $NAMESPACE${NC}"
echo -e "  Restart tunnel:  ${GREEN}kubectl rollout restart daemonset/cloudflared -n $NAMESPACE${NC}"
echo ""

echo -e "${YELLOW}Verification:${NC}"
echo "Wait 1-2 minutes for DNS propagation, then test:"
echo -e "  ${GREEN}curl -I https://api.orangebybni.my.id${NC}"
echo -e "  ${GREEN}dig api.orangebybni.my.id${NC}"
echo ""

echo -e "${YELLOW}Next Steps:${NC}"
echo "1. Deploy your microservices (api-gateway, etc.)"
echo "2. Update ingress rules to use your domain"
echo "3. Test end-to-end connectivity"
echo ""
