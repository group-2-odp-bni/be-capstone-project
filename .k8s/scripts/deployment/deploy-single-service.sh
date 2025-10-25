#!/bin/bash
# Deploy single service individually
# Usage: ./deploy-single-service.sh <service-name> [image-tag]

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
NAMESPACE="${NAMESPACE:-orange-wallet}"
CHART_PATH="../../charts/applications/orange-service"
VALUES_PATH="../../values"

# Service name from argument
SERVICE_NAME="$1"
IMAGE_TAG="${2:-latest}"

# Validate arguments
if [ -z "$SERVICE_NAME" ]; then
    echo -e "${RED}Error: Service name required${NC}"
    echo ""
    echo -e "${YELLOW}Usage:${NC}"
    echo "  ./deploy-single-service.sh <service-name> [image-tag]"
    echo ""
    echo -e "${YELLOW}Available services:${NC}"
    echo "  - authentication-service"
    echo "  - user-service"
    echo "  - wallet-service"
    echo "  - transaction-service"
    echo "  - notification-worker"
    echo ""
    echo -e "${YELLOW}Examples:${NC}"
    echo "  ./deploy-single-service.sh authentication-service"
    echo "  ./deploy-single-service.sh wallet-service v1.2.3"
    echo "  ./deploy-single-service.sh user-service main-abc1234"
    exit 1
fi

# Check if values file exists
VALUES_FILE="$VALUES_PATH/${SERVICE_NAME}-values.yaml"
if [ ! -f "$VALUES_FILE" ]; then
    echo -e "${RED}Error: Values file not found: $VALUES_FILE${NC}"
    exit 1
fi

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Deploying Individual Service${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${YELLOW}Service:${NC} $SERVICE_NAME"
echo -e "${YELLOW}Image Tag:${NC} $IMAGE_TAG"
echo -e "${YELLOW}Namespace:${NC} $NAMESPACE"
echo -e "${YELLOW}Values File:${NC} $VALUES_FILE"
echo ""

# Confirm deployment
read -p "Continue with deployment? (yes/no): " confirm
if [ "$confirm" != "yes" ]; then
    echo -e "${YELLOW}Deployment cancelled${NC}"
    exit 0
fi

echo ""
echo -e "${BLUE}[1/4] Checking prerequisites...${NC}"

# Check if namespace exists
if ! kubectl get namespace $NAMESPACE &>/dev/null; then
    echo -e "${RED}Error: Namespace '$NAMESPACE' does not exist${NC}"
    echo -e "${YELLOW}Create it first or deploy infrastructure services${NC}"
    exit 1
fi

# Check if infrastructure services are ready
echo -e "${BLUE}[2/4] Verifying infrastructure services...${NC}"

POSTGRES_READY=$(kubectl get pods -n $NAMESPACE -l app=postgres -o jsonpath='{.items[0].status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "False")
REDIS_READY=$(kubectl get pods -n $NAMESPACE -l app=redis -o jsonpath='{.items[0].status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "False")

if [ "$POSTGRES_READY" != "True" ] || [ "$REDIS_READY" != "True" ]; then
    echo -e "${YELLOW}Warning: Infrastructure services not fully ready${NC}"
    echo -e "  PostgreSQL: $POSTGRES_READY"
    echo -e "  Redis: $REDIS_READY"
    read -p "Continue anyway? (yes/no): " continue_anyway
    if [ "$continue_anyway" != "yes" ]; then
        exit 1
    fi
fi

echo -e "${GREEN}✓ Infrastructure services OK${NC}"

# Deploy service
echo ""
echo -e "${BLUE}[3/4] Deploying $SERVICE_NAME...${NC}"

helm upgrade --install $SERVICE_NAME $CHART_PATH \
    --namespace $NAMESPACE \
    --values $VALUES_FILE \
    --set image.tag=$IMAGE_TAG \
    --set image.pullPolicy=Always \
    --wait \
    --timeout 5m

echo -e "${GREEN}✓ Helm deployment successful${NC}"

# Verify deployment
echo ""
echo -e "${BLUE}[4/4] Verifying deployment...${NC}"

# Wait for rollout
kubectl rollout status deployment/$SERVICE_NAME -n $NAMESPACE --timeout=300s

# Get pod status
echo ""
echo -e "${YELLOW}Pod Status:${NC}"
kubectl get pods -n $NAMESPACE -l app=$SERVICE_NAME

# Get service status
echo ""
echo -e "${YELLOW}Service Status:${NC}"
kubectl get svc -n $NAMESPACE -l app=$SERVICE_NAME

# Check logs for errors
echo ""
echo -e "${YELLOW}Recent Logs (last 10 lines):${NC}"
POD_NAME=$(kubectl get pods -n $NAMESPACE -l app=$SERVICE_NAME -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ -n "$POD_NAME" ]; then
    kubectl logs $POD_NAME -n $NAMESPACE --tail=10
fi

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Deployment Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${YELLOW}Service Details:${NC}"
echo -e "  Name: ${GREEN}$SERVICE_NAME${NC}"
echo -e "  Tag: ${GREEN}$IMAGE_TAG${NC}"
echo -e "  Namespace: ${GREEN}$NAMESPACE${NC}"
echo ""

echo -e "${YELLOW}Useful Commands:${NC}"
echo -e "  View logs:    ${GREEN}kubectl logs -f deployment/$SERVICE_NAME -n $NAMESPACE${NC}"
echo -e "  Describe pod: ${GREEN}kubectl describe pod -l app=$SERVICE_NAME -n $NAMESPACE${NC}"
echo -e "  Scale:        ${GREEN}kubectl scale deployment/$SERVICE_NAME --replicas=2 -n $NAMESPACE${NC}"
echo -e "  Rollback:     ${GREEN}helm rollback $SERVICE_NAME -n $NAMESPACE${NC}"
echo ""

# Optional: Run health check
echo -e "${BLUE}Run health check? (yes/no):${NC} "
read run_health_check

if [ "$run_health_check" = "yes" ]; then
    echo ""
    echo -e "${YELLOW}Running health check...${NC}"

    SERVICE_IP=$(kubectl get svc $SERVICE_NAME -n $NAMESPACE -o jsonpath='{.spec.clusterIP}' 2>/dev/null)

    if [ -n "$SERVICE_IP" ]; then
        kubectl run curl-test-$SERVICE_NAME --rm -i --restart=Never --image=curlimages/curl:latest -- \
            curl -f http://$SERVICE_IP:8080/actuator/health && \
            echo -e "${GREEN}✓ Health check passed!${NC}" || \
            echo -e "${RED}✗ Health check failed!${NC}"
    else
        echo -e "${YELLOW}Service IP not found, skipping health check${NC}"
    fi
fi
