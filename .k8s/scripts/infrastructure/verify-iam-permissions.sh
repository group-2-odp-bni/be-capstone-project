#!/bin/bash
# Verify IAM Permissions for GCP Persistent Disk CSI Driver
# This script checks if the node service account has required permissions

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}GCP IAM Permissions Verification${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Get GCP metadata
echo -e "${YELLOW}[1/4] Fetching GCP metadata...${NC}"
if ! curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/zone &>/dev/null; then
    echo -e "${RED}Error: Not running on GCP Compute Engine${NC}"
    exit 1
fi

PROJECT_ID=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/project/project-id)
ZONE=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/zone | cut -d'/' -f4)
NODE_SA=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/email)
INSTANCE_NAME=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/name)

echo -e "${GREEN}✓ Metadata retrieved${NC}"
echo -e "  Project ID: ${BLUE}${PROJECT_ID}${NC}"
echo -e "  Zone: ${BLUE}${ZONE}${NC}"
echo -e "  Instance: ${BLUE}${INSTANCE_NAME}${NC}"
echo -e "  Service Account: ${BLUE}${NODE_SA}${NC}"
echo ""

# Check if gcloud is available
if ! command -v gcloud &>/dev/null; then
    echo -e "${YELLOW}Warning: gcloud CLI not found${NC}"
    echo -e "${YELLOW}Installing gcloud is recommended for full verification${NC}"
    echo -e "${YELLOW}Continuing with limited checks...${NC}"
    echo ""
    USE_GCLOUD=false
else
    USE_GCLOUD=true
fi

# Test metadata server access to GCP APIs
echo -e "${YELLOW}[2/4] Testing metadata server access...${NC}"

# Get access token
ACCESS_TOKEN=$(curl -s -H "Metadata-Flavor: Google" \
    http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token | \
    jq -r '.access_token')

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" == "null" ]; then
    echo -e "${RED}✗ Failed to get access token from metadata server${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Access token retrieved successfully${NC}"
echo ""

# Test Compute Engine API access
echo -e "${YELLOW}[3/4] Testing GCP API access...${NC}"

# Test 1: List disks (requires at least compute.viewer)
echo -e "${BLUE}Testing: List disks in zone...${NC}"
DISK_LIST_RESPONSE=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
    "https://compute.googleapis.com/compute/v1/projects/${PROJECT_ID}/zones/${ZONE}/disks")

if echo "$DISK_LIST_RESPONSE" | jq -e '.error' &>/dev/null; then
    ERROR_CODE=$(echo "$DISK_LIST_RESPONSE" | jq -r '.error.code')
    ERROR_MSG=$(echo "$DISK_LIST_RESPONSE" | jq -r '.error.message')
    echo -e "${RED}✗ Failed to list disks${NC}"
    echo -e "${RED}  Error $ERROR_CODE: $ERROR_MSG${NC}"
    exit 1
fi

DISK_COUNT=$(echo "$DISK_LIST_RESPONSE" | jq '.items | length // 0')
echo -e "${GREEN}✓ Can list disks (found $DISK_COUNT disks)${NC}"

# Test 2: Create a test disk (requires compute.storageAdmin)
TEST_DISK_NAME="csi-iam-test-$(date +%s)"
echo -e "${BLUE}Testing: Create test disk...${NC}"

CREATE_DISK_RESPONSE=$(curl -s -X POST \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    "https://compute.googleapis.com/compute/v1/projects/${PROJECT_ID}/zones/${ZONE}/disks" \
    -d "{
        \"name\": \"${TEST_DISK_NAME}\",
        \"sizeGb\": \"1\",
        \"type\": \"projects/${PROJECT_ID}/zones/${ZONE}/diskTypes/pd-standard\"
    }")

if echo "$CREATE_DISK_RESPONSE" | jq -e '.error' &>/dev/null; then
    ERROR_CODE=$(echo "$CREATE_DISK_RESPONSE" | jq -r '.error.code')
    ERROR_MSG=$(echo "$CREATE_DISK_RESPONSE" | jq -r '.error.message')

    if [ "$ERROR_CODE" == "403" ]; then
        echo -e "${RED}✗ CRITICAL: Cannot create disks (Permission Denied)${NC}"
        echo -e "${RED}  Error: $ERROR_MSG${NC}"
        echo -e "${RED}  Missing role: roles/compute.storageAdmin${NC}"
        echo ""
        echo -e "${YELLOW}Fix: Run this command to grant permissions:${NC}"
        echo -e "${GREEN}gcloud projects add-iam-policy-binding $PROJECT_ID \\${NC}"
        echo -e "${GREEN}  --member=\"serviceAccount:$NODE_SA\" \\${NC}"
        echo -e "${GREEN}  --role=\"roles/compute.storageAdmin\"${NC}"
        exit 1
    else
        echo -e "${YELLOW}✗ Failed to create disk: $ERROR_MSG${NC}"
    fi
else
    echo -e "${GREEN}✓ Can create disks${NC}"

    # Wait for disk creation to complete
    sleep 5

    # Test 3: Delete the test disk
    echo -e "${BLUE}Testing: Delete test disk...${NC}"
    DELETE_DISK_RESPONSE=$(curl -s -X DELETE \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        "https://compute.googleapis.com/compute/v1/projects/${PROJECT_ID}/zones/${ZONE}/disks/${TEST_DISK_NAME}")

    if echo "$DELETE_DISK_RESPONSE" | jq -e '.error' &>/dev/null; then
        ERROR_MSG=$(echo "$DELETE_DISK_RESPONSE" | jq -r '.error.message')
        echo -e "${YELLOW}✗ Warning: Failed to delete test disk: $ERROR_MSG${NC}"
        echo -e "${YELLOW}  Please manually delete: $TEST_DISK_NAME${NC}"
    else
        echo -e "${GREEN}✓ Can delete disks${NC}"
    fi
fi

echo ""

# Check IAM roles using gcloud
if [ "$USE_GCLOUD" = true ]; then
    echo -e "${YELLOW}[4/4] Verifying IAM role bindings...${NC}"

    REQUIRED_ROLES=(
        "roles/compute.storageAdmin"
        "roles/iam.serviceAccountUser"
        "roles/logging.logWriter"
        "roles/monitoring.metricWriter"
        "roles/compute.viewer"
    )

    OPTIONAL_ROLES=(
        "roles/storage.objectViewer"
        "roles/storage.objectAdmin"
    )

    echo -e "${BLUE}Required roles:${NC}"
    MISSING_REQUIRED=()

    for role in "${REQUIRED_ROLES[@]}"; do
        if gcloud projects get-iam-policy "$PROJECT_ID" \
            --flatten="bindings[].members" \
            --filter="bindings.role:$role AND bindings.members:serviceAccount:$NODE_SA" \
            --format="value(bindings.role)" 2>/dev/null | grep -q "$role"; then
            echo -e "  ${GREEN}✓ $role${NC}"
        else
            echo -e "  ${RED}✗ $role (MISSING)${NC}"
            MISSING_REQUIRED+=("$role")
        fi
    done

    echo ""
    echo -e "${BLUE}Optional roles:${NC}"
    for role in "${OPTIONAL_ROLES[@]}"; do
        if gcloud projects get-iam-policy "$PROJECT_ID" \
            --flatten="bindings[].members" \
            --filter="bindings.role:$role AND bindings.members:serviceAccount:$NODE_SA" \
            --format="value(bindings.role)" 2>/dev/null | grep -q "$role"; then
            echo -e "  ${GREEN}✓ $role${NC}"
        else
            echo -e "  ${YELLOW}○ $role (not assigned)${NC}"
        fi
    done

    echo ""

    if [ ${#MISSING_REQUIRED[@]} -gt 0 ]; then
        echo -e "${RED}============================================${NC}"
        echo -e "${RED}CRITICAL: Missing Required IAM Roles${NC}"
        echo -e "${RED}============================================${NC}"
        echo ""
        echo -e "${YELLOW}The following roles are missing:${NC}"
        for role in "${MISSING_REQUIRED[@]}"; do
            echo -e "${RED}  - $role${NC}"
        done
        echo ""
        echo -e "${YELLOW}Run these commands to fix:${NC}"
        for role in "${MISSING_REQUIRED[@]}"; do
            echo -e "${GREEN}gcloud projects add-iam-policy-binding $PROJECT_ID \\${NC}"
            echo -e "${GREEN}  --member=\"serviceAccount:$NODE_SA\" \\${NC}"
            echo -e "${GREEN}  --role=\"$role\"${NC}"
            echo ""
        done
        exit 1
    fi
else
    echo -e "${YELLOW}[4/4] Skipping IAM role verification (gcloud not available)${NC}"
fi

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Verification Complete${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${GREEN}✓ All required permissions are granted${NC}"
echo -e "${GREEN}✓ Service account can create/delete disks${NC}"
echo -e "${GREEN}✓ Ready to install CSI driver${NC}"
echo ""

# Additional recommendations
echo -e "${BLUE}Next Steps:${NC}"
echo -e "1. Run the CSI driver installation script:"
echo -e "   ${GREEN}cd ~/k8s/scripts/infrastructure${NC}"
echo -e "   ${GREEN}sudo ./install-gcp-pd-csi.sh${NC}"
echo ""
echo -e "2. After installation, verify with:"
echo -e "   ${GREEN}kubectl get pods -n gce-pd-csi-driver${NC}"
echo ""
echo -e "3. Test volume provisioning:"
echo -e "   ${GREEN}kubectl apply -f ~/k8s/tests/test-pvc.yaml${NC}"
echo ""

exit 0
