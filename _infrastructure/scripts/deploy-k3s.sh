#!/bin/bash
# =============================================================================
# K3s Deployment Script (Ansible Playbooks)
# =============================================================================
# Purpose: Run all Ansible playbooks in sequence
# Usage: ./deploy-k3s.sh
# =============================================================================

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}K3s Cluster Deployment${NC}"
echo -e "${BLUE}========================================${NC}"

cd "$(dirname "$0")/../ansible"

# Install Ansible requirements
echo -e "${BLUE}Installing Ansible requirements...${NC}"
ansible-galaxy install -r requirements.yml

# Playbook 1: Ping test
echo -e "${BLUE}1/4 Testing connectivity...${NC}"
ansible-playbook playbooks/00-ping.yml

# Playbook 2: Prepare nodes
echo -e "${BLUE}2/4 Preparing nodes...${NC}"
ansible-playbook playbooks/01-prepare-nodes.yml

# Playbook 3: Install K3s
echo -e "${BLUE}3/4 Installing K3s...${NC}"
ansible-playbook playbooks/02-install-k3s.yml

# Playbook 4: Configure cluster
echo -e "${BLUE}4/4 Configuring cluster...${NC}"
ansible-playbook playbooks/03-configure-cluster.yml

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}K3s Cluster Deployment Complete!${NC}"
echo -e "${GREEN}========================================${NC}"

echo ""
echo "Kubeconfig saved to: $(pwd)/../kubeconfig"
echo ""
echo "To use the cluster:"
echo "  export KUBECONFIG=$(pwd)/../kubeconfig"
echo "  kubectl get nodes"
