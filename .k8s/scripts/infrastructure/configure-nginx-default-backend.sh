#!/bin/bash
# Configure NGINX Ingress Default Backend for Health Checks
# Deploys a default backend to handle health check requests without Host header

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Configuring NGINX Default Backend${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Check kubectl access
if ! kubectl get nodes &>/dev/null; then
    echo -e "${RED}Cannot access Kubernetes cluster${NC}"
    exit 1
fi

# Check if ingress-nginx namespace exists
if ! kubectl get namespace ingress-nginx &>/dev/null; then
    echo -e "${RED}ingress-nginx namespace not found. Install NGINX Ingress first.${NC}"
    exit 1
fi

echo -e "${YELLOW}[1/3] Deploying default backend...${NC}"

# Deploy default backend
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: default-backend
  namespace: ingress-nginx
  labels:
    app: default-backend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: default-backend
  template:
    metadata:
      labels:
        app: default-backend
    spec:
      containers:
      - name: default-backend
        image: gcr.io/google_containers/defaultbackend-amd64:1.5
        ports:
        - containerPort: 8080
          name: http
        livenessProbe:
          httpGet:
            path: /healthz
            port: 8080
          initialDelaySeconds: 30
          timeoutSeconds: 5
        readinessProbe:
          httpGet:
            path: /healthz
            port: 8080
          initialDelaySeconds: 5
          timeoutSeconds: 5
        resources:
          requests:
            cpu: 10m
            memory: 20Mi
          limits:
            cpu: 50m
            memory: 50Mi
---
apiVersion: v1
kind: Service
metadata:
  name: default-backend
  namespace: ingress-nginx
  labels:
    app: default-backend
spec:
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
    name: http
  selector:
    app: default-backend
  type: ClusterIP
EOF

echo -e "${GREEN}✓ Default backend deployed${NC}"
echo ""

echo -e "${YELLOW}[2/3] Updating NGINX Ingress ConfigMap...${NC}"

# Update nginx ConfigMap to use default backend
kubectl patch configmap nginx-ingress-ingress-nginx-controller \
  -n ingress-nginx \
  --type merge \
  -p '{"data":{"default-backend-service":"ingress-nginx/default-backend"}}'

echo -e "${GREEN}✓ ConfigMap updated${NC}"
echo ""

echo -e "${YELLOW}[3/3] Restarting NGINX Ingress Controller...${NC}"

# Restart nginx controller to apply changes
kubectl rollout restart deployment nginx-ingress-ingress-nginx-controller -n ingress-nginx

# Wait for rollout to complete
kubectl rollout status deployment nginx-ingress-ingress-nginx-controller -n ingress-nginx --timeout=5m

echo -e "${GREEN}✓ NGINX Ingress Controller restarted${NC}"
echo ""

# Wait for default backend to be ready
echo -e "${YELLOW}Waiting for default backend to be ready...${NC}"
kubectl wait --namespace ingress-nginx \
    --for=condition=ready pod \
    --selector=app=default-backend \
    --timeout=60s

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Configuration Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Display status
echo -e "${YELLOW}Default Backend Pods:${NC}"
kubectl get pods -n ingress-nginx -l app=default-backend
echo ""

echo -e "${YELLOW}Default Backend Service:${NC}"
kubectl get svc -n ingress-nginx default-backend
echo ""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Testing Default Backend:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Get node IP for testing
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}' | cut -d' ' -f1)

if [ -n "$NODE_IP" ]; then
    echo -e "Testing health endpoint via NodePort (30080):"
    echo -e "${GREEN}curl http://${NODE_IP}:30080/healthz -v${NC}"
    echo ""
    echo -e "Expected response: ${GREEN}HTTP/1.1 200 OK${NC}"
else
    echo -e "${YELLOW}Could not determine node IP for testing${NC}"
fi

echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Next Steps:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "1. Verify GCP Load Balancer health checks are passing:"
echo -e "   ${GREEN}gcloud compute backend-services get-health \\${NC}"
echo -e "   ${GREEN}    <backend-service-name> --global --project=<project-id>${NC}"
echo ""
echo -e "2. Test external access:"
echo -e "   ${GREEN}curl http://your-domain.com/healthz${NC}"
echo ""
