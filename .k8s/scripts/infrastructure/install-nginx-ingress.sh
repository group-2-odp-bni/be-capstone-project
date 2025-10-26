#!/bin/bash
# NGINX Ingress Controller Installation Script
# Installs NGINX Ingress Controller with NodePort service type for GCP Load Balancer integration

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Installing NGINX Ingress Controller${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Check kubectl access
if ! kubectl get nodes &>/dev/null; then
    echo -e "${RED}Cannot access Kubernetes cluster${NC}"
    exit 1
fi

# NOTE: Using NodePort service type for GCP Load Balancer integration
# MetalLB is NOT compatible with GCP's software-defined networking
echo -e "${GREEN}Using NodePort service type for GCP Load Balancer${NC}"
echo ""

# Create namespace
echo -e "${YELLOW}[1/3] Creating ingress-nginx namespace...${NC}"
kubectl create namespace ingress-nginx --dry-run=client -o yaml | kubectl apply -f -

# Install NGINX Ingress Controller via Helm
echo -e "${YELLOW}[2/3] Installing NGINX Ingress Controller via Helm...${NC}"

# Add NGINX Ingress Helm repository
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx 2>/dev/null || true
helm repo update

# Install NGINX Ingress Controller with NodePort for GCP Load Balancer
helm upgrade --install nginx-ingress ingress-nginx/ingress-nginx \
    --namespace ingress-nginx \
    --set controller.service.type=NodePort \
    --set controller.service.nodePorts.http=30080 \
    --set controller.service.nodePorts.https=30443 \
    --set controller.service.externalTrafficPolicy=Local \
    --set controller.publishService.enabled=false \
    --set controller.metrics.enabled=true \
    --set controller.metrics.serviceMonitor.enabled=false \
    --set controller.podAnnotations."prometheus\.io/scrape"=true \
    --set controller.podAnnotations."prometheus\.io/port"=10254 \
    --set controller.resources.requests.cpu=100m \
    --set controller.resources.requests.memory=128Mi \
    --set controller.resources.limits.cpu=500m \
    --set controller.resources.limits.memory=512Mi \
    --set controller.admissionWebhooks.enabled=false \
    --set controller.ingressClassResource.default=true \
    --wait \
    --timeout 5m

echo -e "${GREEN}NGINX Ingress Controller installed${NC}"

# Wait for NGINX Ingress to be ready
echo -e "${YELLOW}[3/3] Waiting for NGINX Ingress Controller to be ready...${NC}"
kubectl wait --namespace ingress-nginx \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=controller \
    --timeout=300s

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}NGINX Ingress Controller Installed!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Display status
echo -e "${YELLOW}Ingress Controller Pods:${NC}"
kubectl get pods -n ingress-nginx
echo ""

echo -e "${YELLOW}Ingress Controller Service:${NC}"
kubectl get svc -n ingress-nginx
echo ""

# Get NodePort assignments
echo -e "${YELLOW}Getting NodePort assignments...${NC}"
HTTP_NODEPORT=$(kubectl get svc nginx-ingress-ingress-nginx-controller -n ingress-nginx -o jsonpath='{.spec.ports[?(@.name=="http")].nodePort}' 2>/dev/null || echo "")
HTTPS_NODEPORT=$(kubectl get svc nginx-ingress-ingress-nginx-controller -n ingress-nginx -o jsonpath='{.spec.ports[?(@.name=="https")].nodePort}' 2>/dev/null || echo "")

if [ -n "$HTTP_NODEPORT" ] && [ -n "$HTTPS_NODEPORT" ]; then
    echo -e "${GREEN}✓ NodePorts assigned:${NC}"
    echo -e "  HTTP: ${GREEN}${HTTP_NODEPORT}${NC}"
    echo -e "  HTTPS: ${GREEN}${HTTPS_NODEPORT}${NC}"
    echo ""
    echo -e "${YELLOW}NOTE: Access via GCP Load Balancer will be configured via Terraform${NC}"
else
    echo -e "${RED}Failed to get NodePort assignments${NC}"
fi

echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Configuration Details:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "  Service Type: ${GREEN}NodePort${NC}"
echo -e "  HTTP NodePort: ${GREEN}${HTTP_NODEPORT:-30080}${NC}"
echo -e "  HTTPS NodePort: ${GREEN}${HTTPS_NODEPORT:-30443}${NC}"
echo -e "  External Traffic Policy: ${GREEN}Local${NC}"
echo -e "  Metrics: ${GREEN}Enabled${NC}"
echo -e "  Default Ingress Class: ${GREEN}nginx${NC}"
echo ""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Next Steps:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "1. Install Cert-Manager for TLS certificates:"
echo -e "   ${GREEN}./install-cert-manager.sh${NC}"
echo ""
echo -e "2. Create ClusterIssuer for Let's Encrypt:"
echo -e "   ${GREEN}kubectl apply -f ../../manifests/base/cluster-issuer.yaml${NC}"
echo ""
echo -e "3. Deploy your Ingress resources:"
echo -e "   ${GREEN}kubectl apply -f ../../manifests/base/ingress.yaml${NC}"
echo ""
echo -e "4. Deploy GCP Load Balancer via Terraform to get External IP"
echo ""
echo -e "5. Update your DNS records to point to the GCP Load Balancer IP"
echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Testing Ingress (via NodePort):${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "Get a worker node IP:"
echo -e "${GREEN}kubectl get nodes -o wide${NC}"
echo ""
echo -e "Test access (replace NODE_IP with actual worker IP):"
echo -e "${GREEN}curl http://NODE_IP:${HTTP_NODEPORT:-30080}${NC}"
echo ""
echo -e "Or create GCP Load Balancer with Terraform for production access"
echo ""
