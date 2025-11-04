#!/bin/bash
# Database Connection Helper Script
# Provides easy access to PostgreSQL database via port-forward or SSH tunnel

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

NAMESPACE="${NAMESPACE:-orange-wallet}"
LOCAL_PORT="${LOCAL_PORT:-5432}"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Database Connection Helper${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Check prerequisites
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}kubectl not found. Please install kubectl first.${NC}"
    exit 1
fi

# Get database credentials
echo -e "${YELLOW}Fetching database credentials...${NC}"
DB_NAME=$(kubectl get secret app-secrets -n $NAMESPACE -o jsonpath='{.data.DB_NAME}' 2>/dev/null | base64 -d)
DB_USERNAME=$(kubectl get secret app-secrets -n $NAMESPACE -o jsonpath='{.data.DB_USERNAME}' 2>/dev/null | base64 -d)
DB_PASSWORD=$(kubectl get secret app-secrets -n $NAMESPACE -o jsonpath='{.data.DB_PASSWORD}' 2>/dev/null | base64 -d)

if [ -z "$DB_NAME" ] || [ -z "$DB_USERNAME" ]; then
    echo -e "${RED}Failed to fetch database credentials. Check if app-secrets exist.${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Credentials fetched${NC}"
echo ""

# Display connection options
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Choose Connection Method:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "1. ${GREEN}Port Forward${NC} (Recommended for development)"
echo -e "   - Direct connection via kubectl"
echo -e "   - Requires active kubectl access"
echo ""
echo -e "2. ${GREEN}SSH Tunnel via VM${NC} (Recommended for remote access)"
echo -e "   - Connection via SSH to VM"
echo -e "   - More secure for production"
echo ""
echo -e "3. ${GREEN}Show Connection Details${NC}"
echo -e "   - Display credentials only"
echo ""
read -p "Enter choice [1-3]: " choice

case $choice in
    1)
        echo ""
        echo -e "${YELLOW}Starting port-forward...${NC}"
        echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
        echo ""
        echo -e "${GREEN}Connection Details:${NC}"
        echo -e "  Host: localhost"
        echo -e "  Port: ${LOCAL_PORT}"
        echo -e "  Database: ${DB_NAME}"
        echo -e "  Username: ${DB_USERNAME}"
        echo -e "  Password: ${DB_PASSWORD}"
        echo ""
        echo -e "${BLUE}PSQL Command:${NC}"
        echo -e "  ${GREEN}psql -h localhost -p ${LOCAL_PORT} -U ${DB_USERNAME} -d ${DB_NAME}${NC}"
        echo ""
        echo -e "${BLUE}DBeaver/DataGrip/pgAdmin:${NC}"
        echo -e "  Host: localhost"
        echo -e "  Port: ${LOCAL_PORT}"
        echo -e "  Database: ${DB_NAME}"
        echo -e "  Username: ${DB_USERNAME}"
        echo -e "  Password: <shown above>"
        echo ""

        kubectl port-forward -n $NAMESPACE svc/postgres ${LOCAL_PORT}:5432
        ;;

    2)
        echo ""
        echo -e "${YELLOW}Setting up SSH tunnel...${NC}"
        echo ""

        # Get postgres service cluster IP
        POSTGRES_IP=$(kubectl get svc postgres -n $NAMESPACE -o jsonpath='{.spec.clusterIP}')

        if [ -z "$POSTGRES_IP" ]; then
            echo -e "${RED}Failed to get postgres service IP${NC}"
            exit 1
        fi

        echo -e "${BLUE}SSH Tunnel Command:${NC}"
        echo -e "${GREEN}gcloud compute ssh ubuntu@orange-wallet-dev-master \\${NC}"
        echo -e "${GREEN}  --zone=asia-southeast2-a \\${NC}"
        echo -e "${GREEN}  --project=orange-wallet-project \\${NC}"
        echo -e "${GREEN}  -- -L ${LOCAL_PORT}:${POSTGRES_IP}:5432${NC}"
        echo ""
        echo -e "${YELLOW}After tunnel is established, connect using:${NC}"
        echo -e "  Host: localhost"
        echo -e "  Port: ${LOCAL_PORT}"
        echo -e "  Database: ${DB_NAME}"
        echo -e "  Username: ${DB_USERNAME}"
        echo -e "  Password: ${DB_PASSWORD}"
        echo ""

        read -p "Start SSH tunnel now? [y/N]: " start_tunnel
        if [[ "$start_tunnel" =~ ^[Yy]$ ]]; then
            gcloud compute ssh ubuntu@orange-wallet-dev-master \
                --zone=asia-southeast2-a \
                --project=orange-wallet-project \
                -- -L ${LOCAL_PORT}:${POSTGRES_IP}:5432
        fi
        ;;

    3)
        echo ""
        echo -e "${GREEN}============================================${NC}"
        echo -e "${GREEN}Database Connection Details${NC}"
        echo -e "${GREEN}============================================${NC}"
        echo ""
        echo -e "${BLUE}Internal (from within cluster):${NC}"
        echo -e "  Host: postgres.${NAMESPACE}.svc.cluster.local"
        echo -e "  Port: 5432"
        echo -e "  Database: ${DB_NAME}"
        echo -e "  Username: ${DB_USERNAME}"
        echo -e "  Password: ${DB_PASSWORD}"
        echo ""
        echo -e "${BLUE}JDBC URL:${NC}"
        echo -e "  ${GREEN}jdbc:postgresql://postgres:5432/${DB_NAME}${NC}"
        echo ""
        echo -e "${BLUE}PostgreSQL Connection String:${NC}"
        echo -e "  ${GREEN}postgresql://${DB_USERNAME}:${DB_PASSWORD}@postgres:5432/${DB_NAME}${NC}"
        echo ""
        ;;

    *)
        echo -e "${RED}Invalid choice${NC}"
        exit 1
        ;;
esac

echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Security Note:${NC}"
echo -e "${BLUE}============================================${NC}"
echo -e "${YELLOW}⚠ Never expose database directly to the internet${NC}"
echo -e "${YELLOW}⚠ Always use port-forward or SSH tunnel${NC}"
echo -e "${YELLOW}⚠ Keep credentials secure${NC}"
echo ""
