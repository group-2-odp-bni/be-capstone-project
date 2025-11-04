#!/bin/bash
# Expose PostgreSQL for Development Team Access
# Creates a NodePort service for easy database access
# WARNING: Only use for development environment!

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

NAMESPACE="${NAMESPACE:-orange-wallet}"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Expose PostgreSQL for Development${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

echo -e "${RED}⚠️  WARNING: This will expose database to external access!${NC}"
echo -e "${RED}⚠️  Only use in development environment!${NC}"
echo -e "${RED}⚠️  NOT recommended for production!${NC}"
echo ""
read -p "Continue? [y/N]: " confirm

if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 0
fi

echo ""
echo -e "${YELLOW}Creating NodePort service for PostgreSQL...${NC}"

# Create NodePort service
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: postgres-external
  namespace: ${NAMESPACE}
  labels:
    app: postgres
    service: external
spec:
  type: NodePort
  ports:
  - port: 5432
    targetPort: 5432
    protocol: TCP
    nodePort: 30432
  selector:
    app: postgres
EOF

echo -e "${GREEN}✓ NodePort service created${NC}"
echo ""

# Get external IP
EXTERNAL_IP=$(gcloud compute instances list --filter="name:orange-wallet-dev" --format="get(networkInterfaces[0].accessConfigs[0].natIP)" --limit=1 2>/dev/null | head -1)

# Get credentials
DB_NAME=$(kubectl get secret app-secrets -n $NAMESPACE -o jsonpath='{.data.DB_NAME}' 2>/dev/null | base64 -d)
DB_USERNAME=$(kubectl get secret app-secrets -n $NAMESPACE -o jsonpath='{.data.DB_USERNAME}' 2>/dev/null | base64 -d)
DB_PASSWORD=$(kubectl get secret app-secrets -n $NAMESPACE -o jsonpath='{.data.DB_PASSWORD}' 2>/dev/null | base64 -d)

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Database Access Information${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${BLUE}Connection Details:${NC}"
echo -e "  Host: ${GREEN}${EXTERNAL_IP}${NC}"
echo -e "  Port: ${GREEN}30432${NC}"
echo -e "  Database: ${GREEN}${DB_NAME}${NC}"
echo -e "  Username: ${GREEN}${DB_USERNAME}${NC}"
echo -e "  Password: ${GREEN}${DB_PASSWORD}${NC}"
echo ""
echo -e "${BLUE}JDBC URL:${NC}"
echo -e "  ${GREEN}jdbc:postgresql://${EXTERNAL_IP}:30432/${DB_NAME}${NC}"
echo ""
echo -e "${BLUE}DBeaver/DataGrip/pgAdmin:${NC}"
echo -e "  - Host: ${GREEN}${EXTERNAL_IP}${NC}"
echo -e "  - Port: ${GREEN}30432${NC}"
echo -e "  - Database: ${GREEN}${DB_NAME}${NC}"
echo -e "  - Username: ${GREEN}${DB_USERNAME}${NC}"
echo -e "  - Password: ${GREEN}${DB_PASSWORD}${NC}"
echo ""
echo -e "${BLUE}psql Command:${NC}"
echo -e "  ${GREEN}psql -h ${EXTERNAL_IP} -p 30432 -U ${DB_USERNAME} -d ${DB_NAME}${NC}"
echo ""

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}Firewall Configuration${NC}"
echo -e "${YELLOW}============================================${NC}"
echo ""
echo -e "Creating firewall rule to allow access..."
echo ""

# Create firewall rule
gcloud compute firewall-rules create allow-postgres-dev \
    --network=orange-wallet-dev-vpc \
    --allow=tcp:30432 \
    --source-ranges=0.0.0.0/0 \
    --description="Allow PostgreSQL access for development (NodePort 30432)" \
    --project=orange-wallet-project 2>/dev/null || echo -e "${YELLOW}Firewall rule already exists${NC}"

echo ""
echo -e "${GREEN}✓ Firewall configured${NC}"
echo ""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Share with Team:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "Send this information to your team:"
echo ""
echo -e "---"
echo -e "Database Connection:"
echo -e "  Host: ${EXTERNAL_IP}"
echo -e "  Port: 30432"
echo -e "  Database: ${DB_NAME}"
echo -e "  Username: ${DB_USERNAME}"
echo -e "  Password: ${DB_PASSWORD}"
echo -e "---"
echo ""

echo -e "${RED}============================================${NC}"
echo -e "${RED}Security Reminder:${NC}"
echo -e "${RED}============================================${NC}"
echo -e "${YELLOW}⚠️  Database is now accessible from internet${NC}"
echo -e "${YELLOW}⚠️  Make sure to use strong passwords${NC}"
echo -e "${YELLOW}⚠️  Monitor access logs regularly${NC}"
echo -e "${YELLOW}⚠️  To remove access, run: ${GREEN}bash cleanup-db-dev.sh${NC}"
echo ""
