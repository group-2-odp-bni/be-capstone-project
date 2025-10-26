#!/bin/bash
# K3s Worker Node Installation Script
# Joins worker node to K3s cluster with Docker runtime

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}K3s Worker Node Installation${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
  echo -e "${RED}Please run as root or with sudo${NC}"
  exit 1
fi

# Load environment variables if exists
if [ -f ".env" ]; then
    source .env
    echo -e "${BLUE}Loaded configuration from .env${NC}"
fi

# Get node IP
NODE_IP=${NODE_IP:-$(hostname -I | awk '{print $1}')}
echo -e "${YELLOW}Node IP: ${NODE_IP}${NC}"

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker is not installed!${NC}"
    echo -e "${YELLOW}Docker is required for K3s with --docker flag${NC}"
    exit 1
fi

echo -e "${GREEN}Docker version: $(docker --version)${NC}"
echo ""

# Check if K3s is already installed
if command -v k3s &> /dev/null; then
    echo -e "${YELLOW}K3s is already installed on this node${NC}"
    echo -e "${YELLOW}Current version: $(k3s --version | head -n1)${NC}"
    read -p "Do you want to reinstall? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}Installation cancelled${NC}"
        exit 0
    fi

    echo -e "${YELLOW}Uninstalling existing K3s...${NC}"
    /usr/local/bin/k3s-agent-uninstall.sh || true
    sleep 5
fi

# Get K3s master IP and token
if [ -z "$K3S_MASTER_IP" ]; then
    read -p "Enter K3s Master IP address: " K3S_MASTER_IP
fi

if [ -z "$K3S_TOKEN" ]; then
    echo -e "${YELLOW}K3s token not found in environment${NC}"
    read -p "Enter K3s Token from master node: " K3S_TOKEN
fi

if [ -z "$K3S_MASTER_IP" ] || [ -z "$K3S_TOKEN" ]; then
    echo -e "${RED}Master IP and Token are required!${NC}"
    exit 1
fi

echo -e "${BLUE}Master IP: ${K3S_MASTER_IP}${NC}"
echo -e "${BLUE}Worker Node IP: ${NODE_IP}${NC}"
echo ""

# Test connectivity to master
echo -e "${YELLOW}Testing connectivity to master node...${NC}"
if ! ping -c 3 "$K3S_MASTER_IP" > /dev/null 2>&1; then
    echo -e "${RED}Cannot ping master node at ${K3S_MASTER_IP}${NC}"
    echo -e "${YELLOW}Continuing anyway (firewall may block ICMP)...${NC}"
fi

# Test API server connectivity
echo -e "${YELLOW}Testing K3s API server connectivity...${NC}"
if ! curl -k -s --connect-timeout 5 "https://${K3S_MASTER_IP}:6443" > /dev/null; then
    echo -e "${RED}Cannot connect to K3s API server at ${K3S_MASTER_IP}:6443${NC}"
    echo -e "${YELLOW}Please check:${NC}"
    echo -e "  1. Master node is running"
    echo -e "  2. Firewall allows port 6443"
    echo -e "  3. IP address is correct"
    read -p "Continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# K3s version
K3S_VERSION=${K3S_VERSION:-""}
if [ -n "$K3S_VERSION" ]; then
    echo -e "${BLUE}Installing K3s version: ${K3S_VERSION}${NC}"
else
    echo -e "${BLUE}Installing latest stable K3s version${NC}"
fi

# Install K3s agent
echo -e "${YELLOW}[1/3] Installing K3s agent and joining cluster...${NC}"

curl -sfL https://get.k3s.io | \
  INSTALL_K3S_VERSION="${K3S_VERSION}" \
  K3S_URL="https://${K3S_MASTER_IP}:6443" \
  K3S_TOKEN="${K3S_TOKEN}" \
  sh -s - agent \
  --docker \
  --node-ip="${NODE_IP}" \
  --node-external-ip="${NODE_IP}" \
  --kubelet-arg="eviction-hard=memory.available<256Mi,nodefs.available<10%" \
  --kubelet-arg="eviction-soft=memory.available<512Mi,nodefs.available<15%" \
  --kubelet-arg="eviction-soft-grace-period=memory.available=2m,nodefs.available=2m" \
  --kubelet-arg="kube-reserved=cpu=200m,memory=256Mi" \
  --kubelet-arg="system-reserved=cpu=200m,memory=256Mi" \
  --kubelet-arg="max-pods=110"

# Wait for K3s agent to start
echo -e "${YELLOW}[2/3] Waiting for K3s agent to start...${NC}"
sleep 15

# Verify K3s agent is running
echo -e "${YELLOW}[3/3] Verifying K3s agent...${NC}"
systemctl status k3s-agent --no-pager || {
    echo -e "${RED}K3s agent service not running properly${NC}"
    echo -e "${YELLOW}Checking logs...${NC}"
    journalctl -u k3s-agent -n 50 --no-pager
    exit 1
}

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}K3s Worker Node Installation Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${YELLOW}Worker Node Information:${NC}"
echo -e "  K3s Version: ${GREEN}$(k3s --version | head -n1)${NC}"
echo -e "  Container Runtime: ${GREEN}Docker${NC}"
echo -e "  Node IP: ${GREEN}${NODE_IP}${NC}"
echo -e "  Master API: ${GREEN}https://${K3S_MASTER_IP}:6443${NC}"
echo ""
echo -e "${YELLOW}Service Status:${NC}"
systemctl status k3s-agent --no-pager | head -n 10
echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Verification${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "Run this command on the ${GREEN}MASTER NODE${NC} to see this worker:"
echo -e "${GREEN}kubectl get nodes -o wide${NC}"
echo ""
echo -e "If the node doesn't appear after 1-2 minutes, check logs:"
echo -e "${YELLOW}sudo journalctl -u k3s-agent -f${NC}"
echo ""
