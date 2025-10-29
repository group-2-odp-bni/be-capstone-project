#!/bin/bash
# MetalLB Load Balancer Installation Script
# Installs MetalLB with Layer 2 mode for bare-metal/VM load balancing

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Installing MetalLB Load Balancer${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Check kubectl access
if ! kubectl get nodes &>/dev/null; then
    echo -e "${RED}Cannot access Kubernetes cluster${NC}"
    exit 1
fi

# Get the internal IP of the first node
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
echo -e "${YELLOW}Node Internal IP: ${NODE_IP}${NC}"

# Calculate IP range for MetalLB (using last octet range)
IP_BASE=$(echo $NODE_IP | cut -d. -f1-3)
IP_RANGE_START="${IP_BASE}.200"
IP_RANGE_END="${IP_BASE}.250"

# Allow override from environment
METALLB_IP_RANGE_START=${METALLB_IP_RANGE_START:-200}
METALLB_IP_RANGE_END=${METALLB_IP_RANGE_END:-250}

# If environment variables provided, use them
if [ -n "$SUBNET_CIDR" ]; then
    IP_BASE=$(echo $SUBNET_CIDR | cut -d. -f1-3)
fi

IP_RANGE_START="${IP_BASE}.${METALLB_IP_RANGE_START}"
IP_RANGE_END="${IP_BASE}.${METALLB_IP_RANGE_END}"

echo -e "${BLUE}MetalLB IP Range: ${IP_RANGE_START}-${IP_RANGE_END}${NC}"
echo ""

# Install MetalLB using Helm
echo -e "${YELLOW}[1/4] Installing MetalLB via Helm...${NC}"

# Add MetalLB Helm repository
helm repo add metallb https://metallb.github.io/metallb 2>/dev/null || true
helm repo update

# Install MetalLB
helm upgrade --install metallb metallb/metallb \
    --namespace metallb-system \
    --create-namespace \
    --set controller.resources.requests.cpu=50m \
    --set controller.resources.requests.memory=64Mi \
    --set controller.resources.limits.cpu=100m \
    --set controller.resources.limits.memory=128Mi \
    --set speaker.resources.requests.cpu=50m \
    --set speaker.resources.requests.memory=64Mi \
    --set speaker.resources.limits.cpu=100m \
    --set speaker.resources.limits.memory=128Mi \
    --wait \
    --timeout 5m

echo -e "${GREEN}MetalLB installed${NC}"

# Wait for MetalLB pods to be ready
echo -e "${YELLOW}[2/4] Waiting for MetalLB controller to be ready...${NC}"
kubectl wait --namespace metallb-system \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=controller \
    --timeout=300s

echo -e "${YELLOW}[3/4] Waiting for MetalLB speaker to be ready...${NC}"
kubectl wait --namespace metallb-system \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=speaker \
    --timeout=300s

# Create IPAddressPool and L2Advertisement
echo -e "${YELLOW}[4/4] Creating MetalLB IP Address Pool and L2 Advertisement...${NC}"

cat <<EOF | kubectl apply -f -
apiVersion: metallb.io/v1beta1
kind: IPAddressPool
metadata:
  name: default-pool
  namespace: metallb-system
spec:
  addresses:
  - ${IP_RANGE_START}-${IP_RANGE_END}
  autoAssign: true
---
apiVersion: metallb.io/v1beta1
kind: L2Advertisement
metadata:
  name: default
  namespace: metallb-system
spec:
  ipAddressPools:
  - default-pool
EOF

echo -e "${GREEN}MetalLB configuration applied${NC}"

# Small delay for configuration to propagate
sleep 5

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}MetalLB Installed Successfully!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

echo -e "${YELLOW}MetalLB Status:${NC}"
kubectl get pods -n metallb-system
echo ""

echo -e "${YELLOW}IP Address Pool:${NC}"
kubectl get ipaddresspool -n metallb-system
echo ""

echo -e "${YELLOW}L2 Advertisement:${NC}"
kubectl get l2advertisement -n metallb-system
echo ""

echo -e "${BLUE}Configuration:${NC}"
echo -e "  IP Range: ${GREEN}${IP_RANGE_START} - ${IP_RANGE_END}${NC}"
echo -e "  Mode: ${GREEN}Layer 2${NC}"
echo -e "  Auto-assign: ${GREEN}Enabled${NC}"
echo ""

# Check if NGINX Ingress exists and show its IP
if kubectl get namespace ingress-nginx &>/dev/null; then
    if kubectl get svc -n ingress-nginx | grep -q "nginx-ingress-ingress-nginx-controller"; then
        echo -e "${YELLOW}NGINX Ingress Controller detected. Waiting for External IP...${NC}"
        sleep 10

        EXTERNAL_IP=$(kubectl get svc -n ingress-nginx nginx-ingress-ingress-nginx-controller -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

        if [ -n "$EXTERNAL_IP" ]; then
            echo -e "${GREEN}NGINX Ingress External IP: ${EXTERNAL_IP}${NC}"
        else
            echo -e "${YELLOW}External IP not assigned yet. Check later with:${NC}"
            echo -e "${GREEN}kubectl get svc -n ingress-nginx${NC}"
        fi
        echo ""
    fi
fi

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Next Steps:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "1. Install NGINX Ingress Controller (if not installed):"
echo -e "   ${GREEN}./install-nginx-ingress.sh${NC}"
echo ""
echo -e "2. Verify LoadBalancer service gets External IP:"
echo -e "   ${GREEN}kubectl get svc -n ingress-nginx${NC}"
echo ""
echo -e "3. Test LoadBalancer allocation:"
echo -e "   ${GREEN}kubectl create deploy nginx --image=nginx${NC}"
echo -e "   ${GREEN}kubectl expose deploy nginx --port=80 --type=LoadBalancer${NC}"
echo -e "   ${GREEN}kubectl get svc nginx${NC}"
echo ""
