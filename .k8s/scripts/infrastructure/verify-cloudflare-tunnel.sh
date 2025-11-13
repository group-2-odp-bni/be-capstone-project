#!/bin/bash
# Verify Cloudflare Tunnel Status
# Check tunnel health, connectivity, and DNS resolution

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

NAMESPACE="${NAMESPACE:-orange-wallet}"
DOMAIN="orangebybni.my.id"

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Cloudflare Tunnel Verification${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

echo -e "${BLUE}[1/6] Checking Kubernetes resources...${NC}"

# Check DaemonSet
if kubectl get daemonset cloudflared -n $NAMESPACE &>/dev/null; then
    DESIRED=$(kubectl get daemonset cloudflared -n $NAMESPACE -o jsonpath='{.status.desiredNumberScheduled}')
    READY=$(kubectl get daemonset cloudflared -n $NAMESPACE -o jsonpath='{.status.numberReady}')

    echo -e "DaemonSet: ${GREEN}cloudflared${NC}"
    echo "  Desired: $DESIRED"
    echo "  Ready: $READY"

    if [ "$DESIRED" -eq "$READY" ]; then
        echo -e "  Status: ${GREEN}✓ All pods ready${NC}"
    else
        echo -e "  Status: ${YELLOW}⚠ Not all pods ready${NC}"
    fi
else
    echo -e "${RED}✗ DaemonSet not found${NC}"
fi

echo ""

# Check Pods
echo -e "${YELLOW}Pods:${NC}"
kubectl get pods -n $NAMESPACE -l app=cloudflared -o wide

echo ""

echo -e "${BLUE}[2/6] Checking tunnel connectivity...${NC}"

POD_NAME=$(kubectl get pods -n $NAMESPACE -l app=cloudflared -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)

if [ -n "$POD_NAME" ]; then
    # Check logs for connection status
    if kubectl logs $POD_NAME -n $NAMESPACE --tail=50 | grep -q "Registered tunnel connection"; then
        echo -e "${GREEN}✓ Tunnel connected to Cloudflare${NC}"

        # Get connection details
        CONNECTION_INFO=$(kubectl logs $POD_NAME -n $NAMESPACE --tail=100 | grep "Registered tunnel connection" | tail -1)
        echo -e "${YELLOW}Connection: ${NC}$CONNECTION_INFO"
    else
        echo -e "${RED}✗ Tunnel not connected${NC}"
        echo -e "${YELLOW}Recent logs:${NC}"
        kubectl logs $POD_NAME -n $NAMESPACE --tail=10
    fi
else
    echo -e "${RED}✗ No pods found${NC}"
fi

echo ""

echo -e "${BLUE}[3/6] Checking DNS resolution...${NC}"

# Check DNS for each subdomain
SUBDOMAINS=("api" "auth" "wallet")

for subdomain in "${SUBDOMAINS[@]}"; do
    FULL_DOMAIN="${subdomain}.${DOMAIN}"

    if command -v dig &> /dev/null; then
        echo -e "${YELLOW}Checking $FULL_DOMAIN:${NC}"
        DNS_RESULT=$(dig +short $FULL_DOMAIN | head -1)

        if [ -n "$DNS_RESULT" ]; then
            echo -e "  ${GREEN}✓ Resolved to: $DNS_RESULT${NC}"
        else
            echo -e "  ${RED}✗ Not resolved${NC}"
        fi
    else
        echo -e "${YELLOW}dig command not available, using nslookup...${NC}"
        nslookup $FULL_DOMAIN
    fi
done

echo ""

echo -e "${BLUE}[4/6] Testing HTTPS endpoints...${NC}"

if command -v curl &> /dev/null; then
    for subdomain in "${SUBDOMAINS[@]}"; do
        FULL_URL="https://${subdomain}.${DOMAIN}"

        echo -e "${YELLOW}Testing $FULL_URL:${NC}"

        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 $FULL_URL || echo "000")

        case $HTTP_CODE in
            200|301|302|404)
                echo -e "  ${GREEN}✓ Reachable (HTTP $HTTP_CODE)${NC}"
                ;;
            000)
                echo -e "  ${RED}✗ Connection timeout or failed${NC}"
                ;;
            *)
                echo -e "  ${YELLOW}⚠ Response code: $HTTP_CODE${NC}"
                ;;
        esac
    done
else
    echo -e "${YELLOW}curl not available, skipping HTTP tests${NC}"
fi

echo ""

echo -e "${BLUE}[5/6] Checking metrics endpoint...${NC}"

# Port forward to metrics endpoint
echo -e "${YELLOW}Attempting to access metrics...${NC}"

kubectl port-forward -n $NAMESPACE svc/cloudflared-metrics 2000:2000 &
PF_PID=$!

# Wait for port-forward to be ready
sleep 3

if curl -s http://localhost:2000/metrics > /dev/null; then
    echo -e "${GREEN}✓ Metrics endpoint accessible${NC}"

    # Get some key metrics
    echo -e "${YELLOW}Sample metrics:${NC}"
    curl -s http://localhost:2000/metrics | grep -E "(cloudflared_tunnel_total_requests|cloudflared_tunnel_response_by_code)" | head -5
else
    echo -e "${RED}✗ Metrics endpoint not accessible${NC}"
fi

# Clean up port-forward
kill $PF_PID 2>/dev/null || true

echo ""

echo -e "${BLUE}[6/6] Health summary...${NC}"

# Overall health check
ISSUES=0

# Check DaemonSet
if [ "$DESIRED" -ne "$READY" ]; then
    echo -e "${RED}✗ Not all DaemonSet pods are ready${NC}"
    ((ISSUES++))
fi

# Check pod status
POD_STATUS=$(kubectl get pods -n $NAMESPACE -l app=cloudflared -o jsonpath='{.items[0].status.phase}' 2>/dev/null || echo "Unknown")
if [ "$POD_STATUS" != "Running" ]; then
    echo -e "${RED}✗ Pod not in Running state: $POD_STATUS${NC}"
    ((ISSUES++))
fi

# Check tunnel connection
if ! kubectl logs $POD_NAME -n $NAMESPACE --tail=50 2>/dev/null | grep -q "Registered tunnel connection"; then
    echo -e "${RED}✗ Tunnel not connected to Cloudflare${NC}"
    ((ISSUES++))
fi

echo ""

if [ $ISSUES -eq 0 ]; then
    echo -e "${GREEN}============================================${NC}"
    echo -e "${GREEN}✓ All checks passed!${NC}"
    echo -e "${GREEN}Tunnel is healthy and operational${NC}"
    echo -e "${GREEN}============================================${NC}"
else
    echo -e "${YELLOW}============================================${NC}"
    echo -e "${YELLOW}⚠ $ISSUES issue(s) detected${NC}"
    echo -e "${YELLOW}============================================${NC}"
    echo ""
    echo -e "${YELLOW}Troubleshooting tips:${NC}"
    echo "1. Check pod logs: kubectl logs -f daemonset/cloudflared -n $NAMESPACE"
    echo "2. Describe pod: kubectl describe pod -l app=cloudflared -n $NAMESPACE"
    echo "3. Check credentials: kubectl get secret cloudflared-credentials -n $NAMESPACE"
    echo "4. Verify config: kubectl get configmap cloudflared-config -n $NAMESPACE -o yaml"
fi

echo ""
