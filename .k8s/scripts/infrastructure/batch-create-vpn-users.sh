#!/bin/bash
# Batch Create VPN Users for Orange Wallet Team
# This script creates VPN certificates and Linux users for all team members

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Team members list
TEAM_MEMBERS=(
    "opang"
    "naufal"
    "steven"
    "belinda"
    "rully"
    "ahong"
    "yoga"
    "bima"
    "ziva"
)

# Default password (users should change this after first login)
DEFAULT_PASSWORD="Orange2025!"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Batch VPN User Creation${NC}"
echo -e "${BLUE}Orange Wallet Team - $(date)${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${YELLOW}This script requires sudo privileges${NC}"
    exit 1
fi

echo -e "${YELLOW}Creating VPN access for ${#TEAM_MEMBERS[@]} team members...${NC}"
echo ""

SUCCESS_COUNT=0
FAILED_COUNT=0
FAILED_USERS=()

for USERNAME in "${TEAM_MEMBERS[@]}"; do
    echo -e "${BLUE}[$(($SUCCESS_COUNT + $FAILED_COUNT + 1))/${#TEAM_MEMBERS[@]}] Processing: $USERNAME${NC}"

    # Step 1: Create OpenVPN certificate
    echo -n "  - Generating certificate... "
    if /root/openvpn-scripts/create-client.sh "$USERNAME" &>/dev/null; then
        echo -e "${GREEN}✓${NC}"
    else
        echo -e "${YELLOW}✗ (may already exist)${NC}"
    fi

    # Step 2: Create Linux user
    echo -n "  - Creating Linux user... "
    if id "$USERNAME" &>/dev/null; then
        echo -e "${YELLOW}✗ (user already exists)${NC}"
        # Reset password for existing user
        echo "$USERNAME:$DEFAULT_PASSWORD" | chpasswd
        echo -e "    ${GREEN}✓ Password reset${NC}"
    else
        # Create user with default password (non-interactive)
        if useradd -m -s /bin/bash "$USERNAME" && echo "$USERNAME:$DEFAULT_PASSWORD" | chpasswd; then
            echo -e "${GREEN}✓${NC}"
        else
            echo -e "${YELLOW}✗ Failed${NC}"
            FAILED_COUNT=$((FAILED_COUNT + 1))
            FAILED_USERS+=("$USERNAME")
            continue
        fi
    fi

    # Step 3: Copy .ovpn to accessible location
    echo -n "  - Preparing .ovpn file... "
    if [ -f "/root/openvpn-clients/$USERNAME.ovpn" ]; then
        cp "/root/openvpn-clients/$USERNAME.ovpn" "/home/ubuntu/"
        chown ubuntu:ubuntu "/home/ubuntu/$USERNAME.ovpn"
        echo -e "${GREEN}✓${NC}"
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    else
        echo -e "${YELLOW}✗ File not found${NC}"
        FAILED_COUNT=$((FAILED_COUNT + 1))
        FAILED_USERS+=("$USERNAME")
    fi

    echo ""
done

echo -e "${BLUE}============================================${NC}"
echo -e "${GREEN}Batch Creation Complete!${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "${GREEN}Successfully created: $SUCCESS_COUNT users${NC}"
if [ $FAILED_COUNT -gt 0 ]; then
    echo -e "${YELLOW}Failed: $FAILED_COUNT users${NC}"
    echo -e "${YELLOW}Failed users: ${FAILED_USERS[*]}${NC}"
fi
echo ""

echo -e "${BLUE}Files Location:${NC}"
echo -e "  Certificates: ${GREEN}/root/openvpn-clients/*.ovpn${NC}"
echo -e "  Ready to download: ${GREEN}/home/ubuntu/*.ovpn${NC}"
echo ""

echo -e "${BLUE}Download all files:${NC}"
echo -e "  ${GREEN}gcloud compute scp 'orange-wallet-dev-master:/home/ubuntu/*.ovpn' ./vpn-configs/ \\${NC}"
echo -e "  ${GREEN}  --zone=asia-southeast2-a \\${NC}"
echo -e "  ${GREEN}  --project=orange-wallet-project${NC}"
echo ""

echo -e "${BLUE}Default Credentials:${NC}"
echo -e "  Username: ${GREEN}<team-member-name>${NC}"
echo -e "  Password: ${GREEN}$DEFAULT_PASSWORD${NC}"
echo -e "  ${YELLOW}⚠ Users should change password after first login!${NC}"
echo ""

echo -e "${BLUE}List of created files:${NC}"
ls -lh /home/ubuntu/*.ovpn 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}'
echo ""

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}Next Steps:${NC}"
echo -e "${YELLOW}============================================${NC}"
echo -e "1. Download all .ovpn files to local machine"
echo -e "2. Distribute .ovpn files to respective team members"
echo -e "3. Share default password: ${GREEN}$DEFAULT_PASSWORD${NC}"
echo -e "4. Instruct users to change password after first login:"
echo -e "   ${GREEN}passwd${NC} (after SSH to server)"
echo ""
