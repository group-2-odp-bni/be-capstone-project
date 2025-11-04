#!/bin/bash
# Cleanup PostgreSQL Development Exposure
# Removes NodePort service and firewall rule

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

NAMESPACE="${NAMESPACE:-orange-wallet}"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Cleanup PostgreSQL Development Access${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

echo -e "${YELLOW}Removing NodePort service...${NC}"
kubectl delete svc postgres-external -n $NAMESPACE 2>/dev/null || echo "Service not found"

echo -e "${YELLOW}Removing firewall rule...${NC}"
gcloud compute firewall-rules delete allow-postgres-dev \
    --project=orange-wallet-project \
    --quiet 2>/dev/null || echo "Firewall rule not found"

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Cleanup Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo -e "Database is no longer accessible from external network"
echo ""
