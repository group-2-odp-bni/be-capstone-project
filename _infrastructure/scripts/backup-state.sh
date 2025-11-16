#!/bin/bash
# =============================================================================
# Backup Script - Terraform State & Kubeconfig
# =============================================================================
# Purpose: Backup critical infrastructure files
# Usage: ./backup-state.sh
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUP_DIR="${PROJECT_ROOT}/backups"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_PATH="${BACKUP_DIR}/${TIMESTAMP}"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Infrastructure Backup${NC}"
echo -e "${BLUE}========================================${NC}"

# Create backup directory
mkdir -p "$BACKUP_PATH"

echo -e "${BLUE}Backup location: ${BACKUP_PATH}${NC}"
echo ""

# -----------------------------------------------------------------------------
# Backup Terraform Bootstrap State
# -----------------------------------------------------------------------------
echo -e "${BLUE}Backing up Terraform bootstrap state...${NC}"
cd "${PROJECT_ROOT}/terraform/bootstrap"

if [ -f "terraform.tfstate" ]; then
    terraform state pull > "${BACKUP_PATH}/bootstrap-state.json"
    echo -e "${GREEN}✓${NC} Bootstrap state backed up"
else
    echo "⚠ No local bootstrap state (using remote)"
fi

# -----------------------------------------------------------------------------
# Backup Main Terraform State
# -----------------------------------------------------------------------------
echo -e "${BLUE}Backing up main Terraform state...${NC}"
cd "${PROJECT_ROOT}/terraform/environments/dev"

terraform state pull > "${BACKUP_PATH}/terraform-state.json"
echo -e "${GREEN}✓${NC} Terraform state backed up"

# Backup Terraform outputs
terraform output -json > "${BACKUP_PATH}/terraform-outputs.json"
echo -e "${GREEN}✓${NC} Terraform outputs backed up"

# -----------------------------------------------------------------------------
# Backup Kubeconfig
# -----------------------------------------------------------------------------
echo -e "${BLUE}Backing up kubeconfig...${NC}"
if [ -f "${PROJECT_ROOT}/kubeconfig" ]; then
    cp "${PROJECT_ROOT}/kubeconfig" "${BACKUP_PATH}/kubeconfig"
    echo -e "${GREEN}✓${NC} Kubeconfig backed up"
else
    echo "⚠ Kubeconfig not found"
fi

# -----------------------------------------------------------------------------
# Backup Ansible Inventory
# -----------------------------------------------------------------------------
echo -e "${BLUE}Backing up Ansible inventory...${NC}"
if [ -f "${PROJECT_ROOT}/ansible/inventory/hosts.ini" ]; then
    cp "${PROJECT_ROOT}/ansible/inventory/hosts.ini" "${BACKUP_PATH}/ansible-inventory.ini"
    echo -e "${GREEN}✓${NC} Ansible inventory backed up"
fi

# -----------------------------------------------------------------------------
# Create Archive
# -----------------------------------------------------------------------------
echo ""
echo -e "${BLUE}Creating compressed archive...${NC}"
cd "$BACKUP_DIR"
tar -czf "${TIMESTAMP}.tar.gz" "${TIMESTAMP}/"
echo -e "${GREEN}✓${NC} Archive created: ${TIMESTAMP}.tar.gz"

# Cleanup uncompressed backup
rm -rf "$BACKUP_PATH"

# -----------------------------------------------------------------------------
# Backup to GCS (Optional)
# -----------------------------------------------------------------------------
echo ""
echo -e "${BLUE}Upload to GCS? (y/n)${NC}"
read -r UPLOAD_GCS

if [ "$UPLOAD_GCS" = "y" ]; then
    GCS_BUCKET="orange-wallet-tf-state"
    echo "Uploading to gs://${GCS_BUCKET}/backups/"
    gsutil cp "${TIMESTAMP}.tar.gz" "gs://${GCS_BUCKET}/backups/"
    echo -e "${GREEN}✓${NC} Uploaded to GCS"
fi

# -----------------------------------------------------------------------------
# Cleanup Old Backups (keep last 7)
# -----------------------------------------------------------------------------
echo ""
echo -e "${BLUE}Cleaning up old backups (keeping last 7)...${NC}"
cd "$BACKUP_DIR"
ls -t *.tar.gz | tail -n +8 | xargs -r rm
echo -e "${GREEN}✓${NC} Old backups cleaned"

# -----------------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------------
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Backup Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Backup archive: ${BACKUP_DIR}/${TIMESTAMP}.tar.gz"
echo ""
echo "To restore:"
echo "  tar -xzf ${TIMESTAMP}.tar.gz"
echo "  cd ${TIMESTAMP}/"
echo "  # Manually restore files as needed"
echo ""
echo "Available backups:"
ls -lh "${BACKUP_DIR}"/*.tar.gz 2>/dev/null || echo "No backups found"
