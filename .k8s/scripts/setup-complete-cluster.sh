#!/bin/bash
# Complete K3s Cluster Setup Script
# Automates the complete deployment process for orange-wallet microservices

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Orange Wallet K3s Cluster Setup${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"
if ! command_exists kubectl; then
    echo -e "${RED}kubectl not found. Please install kubectl first.${NC}"
    exit 1
fi

if ! command_exists helm; then
    echo -e "${RED}helm not found. Please install helm first.${NC}"
    exit 1
fi

if ! kubectl get nodes &>/dev/null; then
    echo -e "${RED}Cannot access Kubernetes cluster. Please configure kubectl first.${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Prerequisites check passed${NC}"
echo ""

# Step 1: Install NGINX Ingress Controller
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Step 1: Installing NGINX Ingress Controller${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

if kubectl get namespace ingress-nginx &>/dev/null; then
    echo -e "${YELLOW}ingress-nginx namespace already exists, skipping...${NC}"
else
    cd "$SCRIPT_DIR/infrastructure"
    bash install-nginx-ingress.sh
fi
echo ""

# Step 2: Configure NGINX Default Backend
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Step 2: Configuring Default Backend${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

cd "$SCRIPT_DIR/infrastructure"
bash configure-nginx-default-backend.sh
echo ""

# Step 3: Install GCP Persistent Disk CSI Driver (if using GCP storage)
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Step 3: Installing GCP PD CSI Driver${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

if kubectl get csidriver pd.csi.storage.gke.io &>/dev/null; then
    echo -e "${YELLOW}GCP PD CSI Driver already installed, skipping...${NC}"
else
    cd "$SCRIPT_DIR/infrastructure"
    bash install-gcp-pd-csi.sh
fi
echo ""

# Step 4: Deploy Infrastructure Services
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Step 4: Deploying Infrastructure Services${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

cd "$SCRIPT_DIR/deployment"
bash deploy-infrastructure.sh
echo ""

# Step 5: Wait for infrastructure to be ready
echo -e "${YELLOW}Waiting for infrastructure services to be ready...${NC}"
sleep 10
echo ""

# Step 6: Deploy Application Services
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Step 5: Deploying Application Services${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

cd "$SCRIPT_DIR/deployment"
bash deploy-applications.sh
echo ""

# Display final status
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Cluster Setup Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

echo -e "${YELLOW}Cluster Status:${NC}"
echo ""

echo -e "${BLUE}Nodes:${NC}"
kubectl get nodes -o wide
echo ""

echo -e "${BLUE}Infrastructure Services (orange-wallet):${NC}"
kubectl get pods,svc,pvc -n orange-wallet
echo ""

echo -e "${BLUE}Ingress Controller (ingress-nginx):${NC}"
kubectl get pods,svc -n ingress-nginx
echo ""

echo -e "${BLUE}Ingress Resources:${NC}"
kubectl get ingress -n orange-wallet
echo ""

# Get NodePort assignments
HTTP_NODEPORT=$(kubectl get svc nginx-ingress-ingress-nginx-controller -n ingress-nginx -o jsonpath='{.spec.ports[?(@.name=="http")].nodePort}' 2>/dev/null || echo "")
HTTPS_NODEPORT=$(kubectl get svc nginx-ingress-ingress-nginx-controller -n ingress-nginx -o jsonpath='{.spec.ports[?(@.name=="https")].nodePort}' 2>/dev/null || echo "")

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Important Information:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "HTTP NodePort: ${GREEN}${HTTP_NODEPORT}${NC}"
echo -e "HTTPS NodePort: ${GREEN}${HTTPS_NODEPORT}${NC}"
echo ""

# Get Load Balancer IP if available
LB_IP=$(kubectl get ingress -n orange-wallet -o jsonpath='{.items[0].status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
if [ -n "$LB_IP" ]; then
    echo -e "Load Balancer IP: ${GREEN}${LB_IP}${NC}"
else
    echo -e "Load Balancer IP: ${YELLOW}Pending (use Terraform to create GCP Load Balancer)${NC}"
fi
echo ""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Next Steps:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "1. Deploy GCP Load Balancer via Terraform:"
echo -e "   ${GREEN}cd ../../terraform${NC}"
echo -e "   ${GREEN}terraform apply${NC}"
echo ""
echo -e "2. Update DNS records to point to Load Balancer IP"
echo ""
echo -e "3. Verify health checks are passing:"
echo -e "   ${GREEN}gcloud compute backend-services get-health \\${NC}"
echo -e "   ${GREEN}    <backend-service-name> --global --project=<project-id>${NC}"
echo ""
echo -e "4. Test external access:"
echo -e "   ${GREEN}curl http://your-domain.com/actuator/health${NC}"
echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Setup Complete - Happy Deploying! 🚀${NC}"
echo -e "${GREEN}============================================${NC}"
