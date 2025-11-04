#!/bin/bash
# Download All VPN Configurations from GCP VM
# Run this from your local machine

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

VM_NAME="orange-wallet-dev-master"
ZONE="asia-southeast2-a"
PROJECT="orange-wallet-project"
OUTPUT_DIR="./vpn-configs"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Download VPN Configurations${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo -e "${YELLOW}Downloading .ovpn files from VM...${NC}"
echo ""

# Get list of files from VM
echo "Fetching file list..."
FILES=$(gcloud compute ssh $VM_NAME \
    --zone=$ZONE \
    --project=$PROJECT \
    --command="ls -1 /home/ubuntu/*.ovpn 2>/dev/null" || echo "")

if [ -z "$FILES" ]; then
    echo -e "${YELLOW}No .ovpn files found on VM!${NC}"
    echo "Please run batch-create-vpn-users.sh on the VM first."
    exit 1
fi

# Download each file
COUNT=0
for FILE in $FILES; do
    FILENAME=$(basename "$FILE")
    echo -n "Downloading $FILENAME... "

    if gcloud compute ssh $VM_NAME \
        --zone=$ZONE \
        --project=$PROJECT \
        --command="cat $FILE" > "$OUTPUT_DIR/$FILENAME" 2>/dev/null; then
        echo -e "${GREEN}✓${NC}"
        COUNT=$((COUNT + 1))
    else
        echo -e "${YELLOW}✗ Failed${NC}"
    fi
done

echo ""
echo -e "${GREEN}Downloaded $COUNT files to: $OUTPUT_DIR/${NC}"
echo ""

# List downloaded files
echo -e "${BLUE}Downloaded files:${NC}"
ls -lh "$OUTPUT_DIR"/*.ovpn 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}'
echo ""

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}Next Steps:${NC}"
echo -e "${YELLOW}============================================${NC}"
echo "1. Distribute .ovpn files to team members:"
echo "   - opang.ovpn → Opang"
echo "   - naufal.ovpn → Naufal"
echo "   - steven.ovpn → Steven"
echo "   - belinda.ovpn → Belinda"
echo "   - rully.ovpn → Rully"
echo "   - ahong.ovpn → Ahong"
echo "   - yoga.ovpn → Yoga"
echo "   - bima.ovpn → Bima"
echo "   - ziva.ovpn → Ziva"
echo ""
echo "2. Share connection instructions (see team-vpn-instructions.md)"
echo ""
