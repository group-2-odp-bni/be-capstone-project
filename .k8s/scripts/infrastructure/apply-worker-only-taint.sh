#!/bin/bash
# Apply taint to master node to prevent application pods from scheduling there
# This follows Kubernetes best practices for production environments

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Applying Production Best Practice${NC}"
echo -e "${GREEN}Master Node: Control Plane Only${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Check kubectl access
if ! kubectl get nodes &>/dev/null; then
    echo -e "${RED}Cannot access Kubernetes cluster${NC}"
    exit 1
fi

echo -e "${YELLOW}Current node status:${NC}"
kubectl get nodes -o wide
echo ""

echo -e "${YELLOW}Applying taint to master node...${NC}"
echo -e "${BLUE}This will prevent regular pods from scheduling on master${NC}"
echo ""

# Apply NoSchedule taint to master
kubectl taint nodes orange-wallet-dev-master node-role.kubernetes.io/master=:NoSchedule --overwrite

echo -e "${GREEN}✓ Taint applied successfully${NC}"
echo ""

echo -e "${YELLOW}Verifying taint:${NC}"
kubectl get node orange-wallet-dev-master -o jsonpath='{.spec.taints}' | jq .
echo ""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}What This Means:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "✅ Control plane pods (kube-system): Can run on master"
echo -e "✅ Infrastructure pods with tolerations: Can run on master"
echo -e "   - MetalLB, Cert-Manager, Ingress (already have tolerations)"
echo ""
echo -e "❌ Application pods WITHOUT tolerations: ONLY on worker"
echo -e "   - PostgreSQL, Redis, Kafka"
echo -e "   - All your microservices"
echo ""

echo -e "${YELLOW}Current pod distribution:${NC}"
kubectl get pods -A -o wide | awk '{print $8}' | sort | uniq -c
echo ""

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Master node configured for control plane only!${NC}"
echo -e "${GREEN}Application pods will only schedule on worker${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

echo -e "${BLUE}To revert this change (allow pods on master again):${NC}"
echo -e "${GREEN}kubectl taint nodes orange-wallet-dev-master node-role.kubernetes.io/master:NoSchedule-${NC}"
echo ""
