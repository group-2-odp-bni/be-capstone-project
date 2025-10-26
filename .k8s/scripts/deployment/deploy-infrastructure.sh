#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

NAMESPACE="${NAMESPACE:-orange-wallet}"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Deploying Infrastructure Services${NC}"
echo -e "${GREEN}========================================${NC}"

# Create namespace
echo -e "${YELLOW}Creating namespace: $NAMESPACE${NC}"
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# Deploy secrets
echo -e "${YELLOW}Deploying secrets...${NC}"
if [ -f "../manifests/base/secrets.yaml" ]; then
  kubectl apply -f ../manifests/base/secrets.yaml -n $NAMESPACE
else
  echo -e "${RED}Warning: secrets.yaml not found. Please create it.${NC}"
fi

# Deploy configmap
echo -e "${YELLOW}Deploying configmap...${NC}"
if [ -f "../manifests/base/configmap.yaml" ]; then
  kubectl apply -f ../manifests/base/configmap.yaml -n $NAMESPACE
fi

# Deploy PostgreSQL
echo -e "${YELLOW}Deploying PostgreSQL...${NC}"
helm upgrade --install postgres ../charts/infrastructure/postgresql \
  --namespace $NAMESPACE \
  --create-namespace \
  --wait \
  --timeout 5m

# Deploy Redis
echo -e "${YELLOW}Deploying Redis...${NC}"
helm upgrade --install redis ../charts/infrastructure/redis \
  --namespace $NAMESPACE \
  --wait \
  --timeout 5m

# Deploy Kafka
echo -e "${YELLOW}Deploying Kafka...${NC}"
helm upgrade --install kafka ../charts/infrastructure/kafka \
  --namespace $NAMESPACE \
  --wait \
  --timeout 5m

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Infrastructure deployed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"

echo -e "${YELLOW}Infrastructure status:${NC}"
kubectl get pods -n $NAMESPACE
kubectl get svc -n $NAMESPACE
kubectl get pvc -n $NAMESPACE

echo ""
echo -e "${YELLOW}Waiting for all pods to be ready...${NC}"
kubectl wait --for=condition=ready pod --all -n $NAMESPACE --timeout=300s || true

echo -e "${GREEN}Infrastructure is ready for application deployment!${NC}"
