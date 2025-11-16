#!/bin/bash
# =============================================================================
# Complete Infrastructure Deployment Script
# =============================================================================
# Purpose: Deploy Orange Wallet infrastructure from scratch
# Usage: ./deploy-infrastructure.sh
# =============================================================================

set -e  # Exit on error
set -u  # Exit on undefined variable

# -----------------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_DIR="${PROJECT_ROOT}/logs"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
LOG_FILE="${LOG_DIR}/deploy-${TIMESTAMP}.log"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# -----------------------------------------------------------------------------
# Logging Functions
# -----------------------------------------------------------------------------
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "$LOG_FILE"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "$LOG_FILE"
}

# -----------------------------------------------------------------------------
# Setup
# -----------------------------------------------------------------------------
mkdir -p "$LOG_DIR"
log "Starting infrastructure deployment..."
log "Log file: $LOG_FILE"

# -----------------------------------------------------------------------------
# Prerequisites Check
# -----------------------------------------------------------------------------
log "Checking prerequisites..."

command -v gcloud >/dev/null 2>&1 || { log_error "gcloud CLI not found"; exit 1; }
command -v terraform >/dev/null 2>&1 || { log_error "Terraform not found"; exit 1; }
command -v ansible >/dev/null 2>&1 || { log_error "Ansible not found"; exit 1; }
command -v kubectl >/dev/null 2>&1 || { log_error "kubectl not found"; exit 1; }

log_success "All prerequisites found"

# Check gcloud authentication
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | grep -q "@"; then
    log_error "Not authenticated with gcloud. Run: gcloud auth login"
    exit 1
fi

log_success "gcloud authenticated"

# -----------------------------------------------------------------------------
# Phase 1: Terraform Bootstrap
# -----------------------------------------------------------------------------
log "========================================"
log "Phase 1: Terraform Bootstrap (GCS State Bucket)"
log "========================================"

cd "${PROJECT_ROOT}/terraform/bootstrap"

if [ ! -f "terraform.tfvars" ]; then
    log_warning "terraform.tfvars not found in bootstrap/"
    log "Please create terraform.tfvars from terraform.tfvars.example"
    log "Then run this script again"
    exit 1
fi

log "Initializing Terraform..."
terraform init

log "Planning Terraform changes..."
terraform plan -out=tfplan

log "Applying Terraform (creating state bucket)..."
terraform apply tfplan

log_success "Phase 1 complete: State bucket created"

# -----------------------------------------------------------------------------
# Phase 2: Main Infrastructure
# -----------------------------------------------------------------------------
log "========================================"
log "Phase 2: Main Infrastructure (VMs, Network, Storage)"
log "========================================"

cd "${PROJECT_ROOT}/terraform/environments/dev"

if [ ! -f "terraform.tfvars" ]; then
    log_warning "terraform.tfvars not found in environments/dev/"
    log "Please create terraform.tfvars from terraform.tfvars.example"
    exit 1
fi

log "Initializing Terraform with remote backend..."
terraform init

log "Planning infrastructure changes..."
terraform plan -out=tfplan

log "Applying infrastructure..."
terraform apply tfplan

log_success "Phase 2 complete: Infrastructure created"

# Generate Ansible inventory
log "Generating Ansible inventory from Terraform outputs..."
terraform output -json ansible_inventory_data > "${PROJECT_ROOT}/ansible/inventory/terraform-output.json"

# -----------------------------------------------------------------------------
# Phase 3: Generate Ansible Inventory
# -----------------------------------------------------------------------------
log "========================================"
log "Phase 3: Generate Ansible Inventory"
log "========================================"

cd "${SCRIPT_DIR}"
python3 generate-inventory.py

log_success "Phase 3 complete: Ansible inventory generated"

# -----------------------------------------------------------------------------
# Phase 4: Wait for VMs to be Ready
# -----------------------------------------------------------------------------
log "========================================"
log "Phase 4: Wait for VMs to Initialize"
log "========================================"

log "Waiting 60 seconds for cloud-init to complete..."
sleep 60

log_success "Phase 4 complete"

# -----------------------------------------------------------------------------
# Phase 5: Ansible Deployment
# -----------------------------------------------------------------------------
log "========================================"
log "Phase 5: K3s Cluster Deployment (Ansible)"
log "========================================"

cd "${PROJECT_ROOT}/ansible"

log "Testing connectivity..."
ansible all -m ping || {
    log_warning "Connectivity test failed. Waiting 30 more seconds..."
    sleep 30
    ansible all -m ping
}

log "Running Ansible playbooks..."
bash "${SCRIPT_DIR}/deploy-k3s.sh"

log_success "Phase 5 complete: K3s cluster deployed"

# -----------------------------------------------------------------------------
# Phase 6: Kubernetes Bootstrap
# -----------------------------------------------------------------------------
log "========================================"
log "Phase 6: Kubernetes Bootstrap"
log "========================================"

export KUBECONFIG="${PROJECT_ROOT}/kubeconfig"

log "Waiting for cluster to be ready..."
sleep 10

log "Applying bootstrap manifests..."
kubectl apply -k "${PROJECT_ROOT}/kubernetes/bootstrap/"

log_success "Phase 6 complete: Kubernetes bootstrap applied"

# -----------------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------------
log "========================================"
log "DEPLOYMENT COMPLETE!"
log "========================================"

cd "${PROJECT_ROOT}/terraform/environments/dev"
terraform output infrastructure_summary

log ""
log "Next steps:"
log "1. Install ArgoCD:"
log "   kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml"
log ""
log "2. Get ArgoCD password:"
log "   kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d"
log ""
log "3. Deploy infrastructure via ArgoCD:"
log "   kubectl apply -f ${PROJECT_ROOT}/kubernetes/argocd/app-of-apps.yaml"
log ""
log "Full deployment log: $LOG_FILE"

log_success "All phases completed successfully!"
