#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

NAMESPACE="${NAMESPACE:-orange-wallet}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Deploying Application Services${NC}"
echo -e "${GREEN}========================================${NC}"

# Array of services
SERVICES=(
  "authentication-service"
  "user-service"
  "wallet-service"
  "transaction-service"
  "notification-worker"
)

# Deploy each service
for service in "${SERVICES[@]}"; do
  echo -e "${YELLOW}Deploying $service...${NC}"

  helm upgrade --install $service ../charts/applications/orange-service \
    --namespace $NAMESPACE \
    --values ../values/${service}-values.yaml \
    --set image.tag=$IMAGE_TAG \
    --wait \
    --timeout 5m

  echo -e "${GREEN}$service deployed successfully!${NC}"
done

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}All applications deployed!${NC}"
echo -e "${GREEN}========================================${NC}"

echo -e "${YELLOW}Application status:${NC}"
kubectl get pods -n $NAMESPACE
kubectl get svc -n $NAMESPACE

echo ""
echo -e "${YELLOW}Waiting for all application pods to be ready...${NC}"
kubectl wait --for=condition=ready pod -l tier=backend -n $NAMESPACE --timeout=300s || true

echo ""
echo -e "${GREEN}Deployment completed!${NC}"
echo -e "${YELLOW}Access services via:${NC}"
kubectl get ingress -n $NAMESPACE
