#!/bin/bash
# Redeploy infrastructure with GCP Persistent Disk storage
# This script upgrades existing deployments to use GCP PD instead of local-path

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Redeploying Infrastructure with GCP PD${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Check GCP PD CSI is installed
echo -e "${YELLOW}Checking GCP PD CSI Driver...${NC}"
if ! kubectl get storageclass gcp-pd-ssd &>/dev/null; then
    echo -e "${RED}GCP PD StorageClass not found!${NC}"
    echo -e "${BLUE}Install first: cd ../infrastructure && ./install-gcp-pd-csi.sh${NC}"
    exit 1
fi
echo -e "${GREEN}✓ GCP PD CSI Driver ready${NC}"
echo ""

NAMESPACE="orange-wallet"
CHARTS_DIR="../../charts/infrastructure"

# Ensure namespace exists
echo -e "${YELLOW}Ensuring namespace exists...${NC}"
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# Function to upgrade Helm release
upgrade_helm_release() {
    local name=$1
    local chart=$2
    
    echo -e "${YELLOW}Upgrading ${name}...${NC}"
    
    helm upgrade --install $name $CHARTS_DIR/$chart \
        --namespace $NAMESPACE \
        --create-namespace \
        --wait \
        --timeout 10m
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ ${name} upgraded successfully${NC}"
    else
        echo -e "${RED}✗ ${name} upgrade failed${NC}"
        return 1
    fi
    echo ""
}

# Upgrade PostgreSQL
echo -e "${BLUE}[1/3] Upgrading PostgreSQL...${NC}"
upgrade_helm_release "postgresql" "postgresql"

# Wait for PostgreSQL to be ready
echo -e "${YELLOW}Waiting for PostgreSQL to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=postgres -n $NAMESPACE --timeout=300s

# Upgrade Redis
echo -e "${BLUE}[2/3] Upgrading Redis...${NC}"
upgrade_helm_release "redis" "redis"

# Wait for Redis to be ready
echo -e "${YELLOW}Waiting for Redis to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=redis -n $NAMESPACE --timeout=300s

# Upgrade Kafka
echo -e "${BLUE}[3/4] Upgrading Kafka...${NC}"
upgrade_helm_release "kafka" "kafka"

# Wait for Kafka to be ready
echo -e "${YELLOW}Waiting for Kafka to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=kafka -n $NAMESPACE --timeout=300s

# Upgrade WAHA
echo -e "${BLUE}[4/4] Upgrading WAHA...${NC}"
upgrade_helm_release "waha" "waha"

# Wait for WAHA to be ready
echo -e "${YELLOW}Waiting for WAHA to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=waha -n $NAMESPACE --timeout=300s

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Infrastructure Upgrade Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Show status
echo -e "${YELLOW}PVC Status:${NC}"
kubectl get pvc -n $NAMESPACE

echo ""
echo -e "${YELLOW}PV Status:${NC}"
kubectl get pv | grep $NAMESPACE || echo "No PVs yet (WaitForFirstConsumer)"

echo ""
echo -e "${YELLOW}Pod Status:${NC}"
kubectl get pods -n $NAMESPACE

echo ""
echo -e "${YELLOW}Storage Details:${NC}"
kubectl get pvc -n $NAMESPACE -o custom-columns=NAME:.metadata.name,STATUS:.status.phase,STORAGE:.spec.storageClassName,SIZE:.spec.resources.requests.storage

echo ""
echo -e "${GREEN}Next Steps:${NC}"
echo -e "1. Verify PVCs are bound: kubectl get pvc -n $NAMESPACE"
echo -e "2. Check pod logs for any issues"
echo -e "3. Test database connections from application pods"
echo ""
