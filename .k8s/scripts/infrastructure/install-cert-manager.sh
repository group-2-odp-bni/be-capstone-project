#!/bin/bash
# Cert-Manager Installation Script
# Installs cert-manager for automated TLS certificate management

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Installing Cert-Manager${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Check kubectl access
if ! kubectl get nodes &>/dev/null; then
    echo -e "${RED}Cannot access Kubernetes cluster${NC}"
    exit 1
fi

# Cert-Manager version
CERT_MANAGER_VERSION="v1.14.2"

# Install CRDs
echo -e "${YELLOW}[1/4] Installing Cert-Manager CRDs...${NC}"
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/${CERT_MANAGER_VERSION}/cert-manager.crds.yaml

echo -e "${GREEN}CRDs installed${NC}"

# Create namespace
echo -e "${YELLOW}[2/4] Creating cert-manager namespace...${NC}"
kubectl create namespace cert-manager --dry-run=client -o yaml | kubectl apply -f -

# Install Cert-Manager via Helm
echo -e "${YELLOW}[3/4] Installing Cert-Manager via Helm...${NC}"

# Add Jetstack Helm repository
helm repo add jetstack https://charts.jetstack.io 2>/dev/null || true
helm repo update

# Install Cert-Manager
helm upgrade --install cert-manager jetstack/cert-manager \
    --namespace cert-manager \
    --version ${CERT_MANAGER_VERSION} \
    --set installCRDs=false \
    --set resources.requests.cpu=50m \
    --set resources.requests.memory=64Mi \
    --set resources.limits.cpu=200m \
    --set resources.limits.memory=256Mi \
    --set webhook.resources.requests.cpu=50m \
    --set webhook.resources.requests.memory=64Mi \
    --set webhook.resources.limits.cpu=100m \
    --set webhook.resources.limits.memory=128Mi \
    --set cainjector.resources.requests.cpu=50m \
    --set cainjector.resources.requests.memory=64Mi \
    --set cainjector.resources.limits.cpu=100m \
    --set cainjector.resources.limits.memory=128Mi \
    --wait \
    --timeout 5m

echo -e "${GREEN}Cert-Manager installed${NC}"

# Wait for Cert-Manager to be ready
echo -e "${YELLOW}[4/4] Waiting for Cert-Manager to be ready...${NC}"
kubectl wait --namespace cert-manager \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/instance=cert-manager \
    --timeout=300s

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Cert-Manager Installed Successfully!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Display status
echo -e "${YELLOW}Cert-Manager Pods:${NC}"
kubectl get pods -n cert-manager
echo ""

echo -e "${YELLOW}Cert-Manager Version:${NC}"
kubectl get deployment -n cert-manager cert-manager -o jsonpath='{.spec.template.spec.containers[0].image}'
echo ""
echo ""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Configuration:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "  Version: ${GREEN}${CERT_MANAGER_VERSION}${NC}"
echo -e "  Namespace: ${GREEN}cert-manager${NC}"
echo -e "  CRDs: ${GREEN}Installed${NC}"
echo ""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Next Steps:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "1. Create a ClusterIssuer for Let's Encrypt:"
echo ""
echo -e "${GREEN}cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: your-email@example.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
EOF${NC}"
echo ""

echo -e "2. Update your Ingress to use TLS:"
echo ""
echo -e "${GREEN}apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: example-ingress
  annotations:
    cert-manager.io/cluster-issuer: \"letsencrypt-prod\"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - example.com
    secretName: example-tls
  rules:
  - host: example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: example-service
            port:
              number: 80${NC}"
echo ""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Verify Installation:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "Check Cert-Manager webhook is working:"
echo -e "${GREEN}kubectl get validatingwebhookconfigurations | grep cert-manager${NC}"
echo ""
echo -e "Create a test certificate:"
echo -e "${GREEN}kubectl apply -f - <<EOF
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: test-cert
  namespace: default
spec:
  secretName: test-cert-tls
  issuerRef:
    name: letsencrypt-prod
    kind: ClusterIssuer
  dnsNames:
  - test.example.com
EOF${NC}"
echo ""
echo -e "Check certificate status:"
echo -e "${GREEN}kubectl describe certificate test-cert${NC}"
echo ""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Important Notes:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "- Let's Encrypt has rate limits: 50 certs per domain per week"
echo -e "- Use staging issuer for testing:"
echo -e "  ${GREEN}server: https://acme-staging-v02.api.letsencrypt.org/directory${NC}"
echo -e "- Ensure your domain DNS points to the Ingress External IP"
echo -e "- HTTP-01 challenge requires port 80 to be accessible"
echo ""
