#!/bin/bash
# Get Database Credentials for VPN Users
# Run this on the master node to get current database connection details

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

NAMESPACE="orange-wallet"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Database Connection Information${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}kubectl not found${NC}"
    exit 1
fi

# Get database credentials from secrets
echo -e "${YELLOW}Fetching credentials from Kubernetes secrets...${NC}"

DB_NAME=$(kubectl get secret app-secrets -n $NAMESPACE -o jsonpath='{.data.DB_NAME}' 2>/dev/null | base64 -d)
DB_USERNAME=$(kubectl get secret app-secrets -n $NAMESPACE -o jsonpath='{.data.DB_USERNAME}' 2>/dev/null | base64 -d)
DB_PASSWORD=$(kubectl get secret app-secrets -n $NAMESPACE -o jsonpath='{.data.DB_PASSWORD}' 2>/dev/null | base64 -d)

if [ -z "$DB_NAME" ] || [ -z "$DB_USERNAME" ]; then
    echo -e "${RED}Failed to fetch credentials. Check if secrets exist.${NC}"
    exit 1
fi

# Get PostgreSQL service cluster IP
POSTGRES_CLUSTER_IP=$(kubectl get svc postgres -n $NAMESPACE -o jsonpath='{.spec.clusterIP}')

echo -e "${GREEN}✓ Credentials retrieved${NC}"
echo ""

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}VPN Connection Required${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${YELLOW}These credentials only work when connected to OpenVPN${NC}"
echo ""

echo -e "${BLUE}Method 1: Using DNS (Recommended)${NC}"
echo -e "  Host: ${GREEN}postgres.orange-wallet.svc.cluster.local${NC}"
echo -e "  Port: ${GREEN}5432${NC}"
echo ""

echo -e "${BLUE}Method 2: Using ClusterIP (If DNS fails)${NC}"
echo -e "  Host: ${GREEN}$POSTGRES_CLUSTER_IP${NC}"
echo -e "  Port: ${GREEN}5432${NC}"
echo ""

echo -e "${BLUE}Database Credentials:${NC}"
echo -e "  Database: ${GREEN}$DB_NAME${NC}"
echo -e "  Username: ${GREEN}$DB_USERNAME${NC}"
echo -e "  Password: ${GREEN}$DB_PASSWORD${NC}"
echo ""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Connection Examples${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

echo -e "${YELLOW}DBeaver / DataGrip / pgAdmin:${NC}"
echo -e "  Host: ${GREEN}postgres.orange-wallet.svc.cluster.local${NC}"
echo -e "  Port: ${GREEN}5432${NC}"
echo -e "  Database: ${GREEN}$DB_NAME${NC}"
echo -e "  Username: ${GREEN}$DB_USERNAME${NC}"
echo -e "  Password: ${GREEN}$DB_PASSWORD${NC}"
echo ""

echo -e "${YELLOW}JDBC URL:${NC}"
echo -e "  ${GREEN}jdbc:postgresql://postgres.orange-wallet.svc.cluster.local:5432/$DB_NAME${NC}"
echo ""
echo -e "  With credentials:"
echo -e "  ${GREEN}jdbc:postgresql://postgres.orange-wallet.svc.cluster.local:5432/$DB_NAME?user=$DB_USERNAME&password=$DB_PASSWORD${NC}"
echo ""

echo -e "${YELLOW}psql Command:${NC}"
echo -e "  ${GREEN}PGPASSWORD='$DB_PASSWORD' psql -h postgres.orange-wallet.svc.cluster.local -p 5432 -U $DB_USERNAME -d $DB_NAME${NC}"
echo ""

echo -e "${YELLOW}Python (psycopg2):${NC}"
cat <<EOF
  ${GREEN}import psycopg2

conn = psycopg2.connect(
    host="postgres.orange-wallet.svc.cluster.local",
    port=5432,
    database="$DB_NAME",
    user="$DB_USERNAME",
    password="$DB_PASSWORD"
)${NC}
EOF
echo ""

echo -e "${YELLOW}Node.js (pg):${NC}"
cat <<EOF
  ${GREEN}const { Client } = require('pg');

const client = new Client({
  host: 'postgres.orange-wallet.svc.cluster.local',
  port: 5432,
  database: '$DB_NAME',
  user: '$DB_USERNAME',
  password: '$DB_PASSWORD'
});${NC}
EOF
echo ""

echo -e "${YELLOW}Spring Boot (application.yml):${NC}"
cat <<EOF
  ${GREEN}spring:
  datasource:
    url: jdbc:postgresql://postgres.orange-wallet.svc.cluster.local:5432/$DB_NAME
    username: $DB_USERNAME
    password: $DB_PASSWORD
    driver-class-name: org.postgresql.Driver${NC}
EOF
echo ""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Alternative Connection (If DNS Fails)${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "Replace hostname with ClusterIP: ${GREEN}$POSTGRES_CLUSTER_IP${NC}"
echo ""
echo -e "Example:"
echo -e "  ${GREEN}jdbc:postgresql://$POSTGRES_CLUSTER_IP:5432/$DB_NAME${NC}"
echo ""

echo -e "${RED}============================================${NC}"
echo -e "${RED}Security Warning${NC}"
echo -e "${RED}============================================${NC}"
echo -e "${YELLOW}⚠ These are production credentials${NC}"
echo -e "${YELLOW}⚠ Never commit them to git${NC}"
echo -e "${YELLOW}⚠ Never share in public channels${NC}"
echo -e "${YELLOW}⚠ Only use via VPN connection${NC}"
echo ""
