#!/bin/bash
# Deploy OpenVPN Server - Complete Installation Guide
# Execute this script on your local machine to deploy OpenVPN to GCP

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PROJECT_ID="orange-wallet-project"
ZONE="asia-southeast2-a"
MASTER_NODE="orange-wallet-dev-master"
SCRIPTS_DIR="/root/openvpn-scripts"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}OpenVPN Deployment for Orange Wallet${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

if ! command -v gcloud &> /dev/null; then
    echo -e "${RED}gcloud CLI not found. Please install it first.${NC}"
    exit 1
fi

if ! command -v kubectl &> /dev/null; then
    echo -e "${YELLOW}Warning: kubectl not found. You'll need it for database credentials.${NC}"
fi

echo -e "${GREEN}✓ Prerequisites check passed${NC}"
echo ""

# Test SSH connection
echo -e "${YELLOW}Testing SSH connection to master node...${NC}"
if ! gcloud compute ssh $MASTER_NODE --zone=$ZONE --project=$PROJECT_ID --command="echo 'Connection OK'" &> /dev/null; then
    echo -e "${RED}Cannot connect to master node${NC}"
    echo -e "${YELLOW}Please check your GCP permissions and firewall rules${NC}"
    exit 1
fi
echo -e "${GREEN}✓ SSH connection successful${NC}"
echo ""

# Upload installation script
echo -e "${YELLOW}Step 1/4: Uploading installation scripts...${NC}"

gcloud compute scp \
    .k8s/scripts/infrastructure/install-openvpn-server.sh \
    $MASTER_NODE:/tmp/ \
    --zone=$ZONE \
    --project=$PROJECT_ID

gcloud compute scp \
    .k8s/scripts/infrastructure/openvpn-client-manager.sh \
    $MASTER_NODE:/tmp/ \
    --zone=$ZONE \
    --project=$PROJECT_ID

echo -e "${GREEN}✓ Scripts uploaded${NC}"
echo ""

# Execute installation
echo -e "${YELLOW}Step 2/4: Installing OpenVPN server on master node...${NC}"
echo -e "${BLUE}This will take several minutes (generating DH parameters)...${NC}"
echo ""

gcloud compute ssh $MASTER_NODE \
    --zone=$ZONE \
    --project=$PROJECT_ID \
    --command="sudo bash /tmp/install-openvpn-server.sh"

echo ""
echo -e "${GREEN}✓ OpenVPN server installed${NC}"
echo ""

# Move client manager to scripts directory
echo -e "${YELLOW}Step 3/4: Setting up client management...${NC}"

gcloud compute ssh $MASTER_NODE \
    --zone=$ZONE \
    --project=$PROJECT_ID \
    --command="sudo mv /tmp/openvpn-client-manager.sh $SCRIPTS_DIR/ && sudo chmod +x $SCRIPTS_DIR/openvpn-client-manager.sh"

echo -e "${GREEN}✓ Client manager ready${NC}"
echo ""

# Get server information
echo -e "${YELLOW}Step 4/4: Fetching server information...${NC}"

SERVER_IP=$(gcloud compute instances describe $MASTER_NODE \
    --zone=$ZONE \
    --project=$PROJECT_ID \
    --format="get(networkInterfaces[0].accessConfigs[0].natIP)")

echo -e "${GREEN}✓ Server information retrieved${NC}"
echo ""

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}OpenVPN Deployment Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${BLUE}Server Details:${NC}"
echo -e "  Server IP: ${GREEN}$SERVER_IP${NC}"
echo -e "  Port: ${GREEN}1194 (UDP)${NC}"
echo -e "  Status: ${GREEN}Running${NC}"
echo ""
echo -e "${BLUE}Next Steps:${NC}"
echo ""
echo -e "1. ${YELLOW}Create client configuration for your team:${NC}"
echo -e "   ${GREEN}gcloud compute ssh $MASTER_NODE --zone=$ZONE --project=$PROJECT_ID${NC}"
echo -e "   ${GREEN}sudo $SCRIPTS_DIR/openvpn-client-manager.sh${NC}"
echo ""
echo -e "2. ${YELLOW}Or use command line:${NC}"
echo -e "   ${GREEN}gcloud compute ssh $MASTER_NODE --zone=$ZONE --project=$PROJECT_ID \\${NC}"
echo -e "   ${GREEN}  --command=\"sudo $SCRIPTS_DIR/create-client.sh developer1\"${NC}"
echo ""
echo -e "3. ${YELLOW}Download client configuration:${NC}"
echo -e "   ${GREEN}gcloud compute scp $MASTER_NODE:/root/openvpn-clients/developer1.ovpn . \\${NC}"
echo -e "   ${GREEN}  --zone=$ZONE --project=$PROJECT_ID${NC}"
echo ""
echo -e "4. ${YELLOW}Share .ovpn file with team member securely${NC}"
echo ""
echo -e "${BLUE}Quick Commands:${NC}"
echo ""
echo -e "Create client:"
echo -e "  ${GREEN}gcloud compute ssh $MASTER_NODE --zone=$ZONE --project=$PROJECT_ID \\${NC}"
echo -e "  ${GREEN}    --command=\"sudo $SCRIPTS_DIR/create-client.sh USERNAME\"${NC}"
echo ""
echo -e "Download config:"
echo -e "  ${GREEN}gcloud compute scp $MASTER_NODE:/root/openvpn-clients/USERNAME.ovpn . \\${NC}"
echo -e "  ${GREEN}    --zone=$ZONE --project=$PROJECT_ID${NC}"
echo ""
echo -e "Check status:"
echo -e "  ${GREEN}gcloud compute ssh $MASTER_NODE --zone=$ZONE --project=$PROJECT_ID \\${NC}"
echo -e "  ${GREEN}    --command=\"sudo systemctl status openvpn@server\"${NC}"
echo ""
echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}Security Reminders:${NC}"
echo -e "${YELLOW}============================================${NC}"
echo -e "⚠ Firewall rule created allows connections from anywhere"
echo -e "⚠ Client .ovpn files contain certificates - keep secure"
echo -e "⚠ Revoke access when team members leave"
echo -e "⚠ Monitor connected clients regularly"
echo ""
echo -e "${BLUE}For interactive management, SSH to master and run:${NC}"
echo -e "  ${GREEN}sudo $SCRIPTS_DIR/openvpn-client-manager.sh${NC}"
echo ""
