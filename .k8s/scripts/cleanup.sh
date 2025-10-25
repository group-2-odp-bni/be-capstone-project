#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

NAMESPACE="${NAMESPACE:-orange-wallet}"

echo -e "${RED}========================================${NC}"
echo -e "${RED}WARNING: This will delete all resources${NC}"
echo -e "${RED}========================================${NC}"

read -p "Are you sure you want to proceed? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
  echo -e "${GREEN}Cleanup cancelled${NC}"
  exit 0
fi

echo -e "${YELLOW}Deleting application releases...${NC}"
helm uninstall authentication-service -n $NAMESPACE || true
helm uninstall user-service -n $NAMESPACE || true
helm uninstall wallet-service -n $NAMESPACE || true
helm uninstall transaction-service -n $NAMESPACE || true
helm uninstall notification-worker -n $NAMESPACE || true

echo -e "${YELLOW}Deleting infrastructure releases...${NC}"
helm uninstall postgres -n $NAMESPACE || true
helm uninstall redis -n $NAMESPACE || true
helm uninstall kafka -n $NAMESPACE || true

echo -e "${YELLOW}Deleting PVCs...${NC}"
kubectl delete pvc --all -n $NAMESPACE || true

echo -e "${YELLOW}Deleting namespace...${NC}"
kubectl delete namespace $NAMESPACE || true

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Cleanup completed!${NC}"
echo -e "${GREEN}========================================${NC}"
