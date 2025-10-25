#!/bin/bash
# Remove taint from master node to allow application pods
# This enables mixed workload mode (dev/testing setup)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Removing Master Node Taint${NC}"
echo -e "${GREEN}Enable Mixed Workload Mode${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Check kubectl access
if ! kubectl get nodes &>/dev/null; then
    echo -e "${RED}Cannot access Kubernetes cluster${NC}"
    exit 1
fi

echo -e "${YELLOW}Current taint status:${NC}"
kubectl get node orange-wallet-dev-master -o jsonpath='{.spec.taints}'
echo ""
echo ""

echo -e "${YELLOW}Removing taint from master node...${NC}"

# Remove NoSchedule taint from master
kubectl taint nodes orange-wallet-dev-master node-role.kubernetes.io/master:NoSchedule- 2>/dev/null && \
    echo -e "${GREEN}✓ Taint removed successfully${NC}" || \
    echo -e "${YELLOW}No taint found (already removed or never applied)${NC}"

echo ""

echo -e "${YELLOW}Verifying:${NC}"
TAINT=$(kubectl get node orange-wallet-dev-master -o jsonpath='{.spec.taints}')
if [ -z "$TAINT" ] || [ "$TAINT" = "null" ]; then
    echo -e "${GREEN}✓ Master node has NO taints - pods can schedule anywhere${NC}"
else
    echo -e "${YELLOW}Remaining taints:${NC}"
    echo "$TAINT" | jq .
fi
echo ""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}What This Means:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "✅ All pods can now run on BOTH master and worker"
echo -e "✅ Kubernetes scheduler will balance workload"
echo -e "✅ Better resource utilization (both nodes used)"
echo ""
echo -e "⚠️  Note: This is for dev/testing only"
echo -e "⚠️  Production should keep master tainted"
echo ""

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Mixed workload mode enabled!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
