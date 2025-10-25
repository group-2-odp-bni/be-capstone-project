#!/bin/bash
# NGINX Ingress Controller Installation Script
# Installs NGINX Ingress Controller with LoadBalancer service type

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

# Check if MetalLB is installed
if ! kubectl get namespace metallb-system &>/dev/null; then
    echo -e "${RED}MetalLB is not installed!${NC}"
    echo -e "${YELLOW}MetalLB is required for LoadBalancer services${NC}"
    echo -e "${BLUE}Install MetalLB first: ./install-metallb.sh${NC}"
    exit 1
fi

echo -e "${GREEN}MetalLB is installed${NC}"
echo ""

# Create namespace
echo -e "${YELLOW}[1/3] Creating ingress-nginx namespace...${NC}"
kubectl create namespace ingress-nginx --dry-run=client -o yaml | kubectl apply -f -

# Install NGINX Ingress Controller via Helm
echo -e "${YELLOW}[2/3] Installing NGINX Ingress Controller via Helm...${NC}"

# Add NGINX Ingress Helm repository
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx 2>/dev/null || true
helm repo update

# Install NGINX Ingress Controller
helm upgrade --install nginx-ingress ingress-nginx/ingress-nginx \
    --namespace ingress-nginx \
    --set controller.service.type=LoadBalancer \
    --set controller.service.externalTrafficPolicy=Local \
    --set controller.publishService.enabled=true \
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

# Get External IP
echo -e "${YELLOW}Waiting for External IP assignment...${NC}"
sleep 10

EXTERNAL_IP=$(kubectl get svc nginx-ingress-ingress-nginx-controller -n ingress-nginx -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

if [ -n "$EXTERNAL_IP" ]; then
    echo -e "${GREEN}✓ External IP assigned: ${EXTERNAL_IP}${NC}"
    echo ""
    echo -e "${BLUE}Testing NGINX Ingress (HTTP):${NC}"
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://${EXTERNAL_IP} || echo "000")
    if [ "$HTTP_STATUS" = "404" ]; then
        echo -e "${GREEN}✓ NGINX Ingress is responding (404 is expected without ingress rules)${NC}"
    else
        echo -e "${YELLOW}Response code: $HTTP_STATUS${NC}"
    fi
else
    echo -e "${YELLOW}External IP not assigned yet${NC}"
    echo -e "${BLUE}Check again in a few moments:${NC}"
    echo -e "${GREEN}kubectl get svc -n ingress-nginx${NC}"
fi

echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Configuration Details:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "  Service Type: ${GREEN}LoadBalancer${NC}"
echo -e "  External Traffic Policy: ${GREEN}Local${NC}"
echo -e "  Metrics: ${GREEN}Enabled${NC}"
echo -e "  Default Ingress Class: ${GREEN}nginx${NC}"
if [ -n "$EXTERNAL_IP" ]; then
    echo -e "  External IP: ${GREEN}${EXTERNAL_IP}${NC}"
fi
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
echo -e "4. Update your DNS records to point to: ${GREEN}${EXTERNAL_IP}${NC}"
echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Testing Ingress:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "Create a test ingress:"
echo -e "${GREEN}kubectl create deployment nginx --image=nginx${NC}"
echo -e "${GREEN}kubectl expose deployment nginx --port=80${NC}"
echo -e "${GREEN}kubectl create ingress nginx --class=nginx --rule=\"test.example.com/*=nginx:80\"${NC}"
echo ""
echo -e "Then test with:"
echo -e "${GREEN}curl -H 'Host: test.example.com' http://${EXTERNAL_IP}${NC}"
echo ""
