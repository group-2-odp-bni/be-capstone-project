#!/bin/bash
# K3s Master Node Installation Script
# Installs K3s server with Docker runtime, optimized for GCP VMs

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}K3s Master Node Installation${NC}"
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
    echo -e "${YELLOW}K3s is already installed${NC}"
    echo -e "${YELLOW}Current version: $(k3s --version | head -n1)${NC}"
    read -p "Do you want to reinstall? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}Installation cancelled${NC}"
        exit 0
    fi

    echo -e "${YELLOW}Uninstalling existing K3s...${NC}"
    /usr/local/bin/k3s-uninstall.sh || true
    sleep 5
fi

# Prepare K3s installation
echo -e "${YELLOW}[1/6] Preparing K3s installation...${NC}"

# Create K3s config directory
mkdir -p /etc/rancher/k3s

# K3s version
K3S_VERSION=${K3S_VERSION:-""}
if [ -n "$K3S_VERSION" ]; then
    echo -e "${BLUE}Installing K3s version: ${K3S_VERSION}${NC}"
    INSTALL_K3S_VERSION=$K3S_VERSION
else
    echo -e "${BLUE}Installing latest stable K3s version${NC}"
fi

# K3s token
if [ -f "/etc/rancher/k3s/k3s-token" ]; then
    K3S_TOKEN=$(cat /etc/rancher/k3s/k3s-token)
    echo -e "${BLUE}Using existing K3s token${NC}"
elif [ -n "$K3S_TOKEN" ]; then
    echo -e "${BLUE}Using K3s token from environment${NC}"
    echo "$K3S_TOKEN" > /etc/rancher/k3s/k3s-token
    chmod 600 /etc/rancher/k3s/k3s-token
else
    echo -e "${YELLOW}No K3s token provided - will generate random token${NC}"
fi

# Install K3s
echo -e "${YELLOW}[2/6] Installing K3s server...${NC}"

curl -sfL https://get.k3s.io | \
  INSTALL_K3S_VERSION="${K3S_VERSION}" \
  K3S_TOKEN="${K3S_TOKEN}" \
  sh -s - server \
  --docker \
  --write-kubeconfig-mode 644 \
  --disable traefik \
  --disable servicelb \
  --disable local-storage \
  --disable metrics-server \
  --node-ip="${NODE_IP}" \
  --node-external-ip="${NODE_IP}" \
  --kube-apiserver-arg="default-not-ready-toleration-seconds=30" \
  --kube-apiserver-arg="default-unreachable-toleration-seconds=30" \
  --kubelet-arg="eviction-hard=memory.available<256Mi,nodefs.available<10%" \
  --kubelet-arg="eviction-soft=memory.available<512Mi,nodefs.available<15%" \
  --kubelet-arg="eviction-soft-grace-period=memory.available=2m,nodefs.available=2m" \
  --kubelet-arg="kube-reserved=cpu=200m,memory=256Mi" \
  --kubelet-arg="system-reserved=cpu=200m,memory=256Mi" \
  --kubelet-arg="max-pods=110"

# Wait for K3s to be ready
echo -e "${YELLOW}[3/6] Waiting for K3s to be ready...${NC}"
sleep 20

# Verify K3s installation
echo -e "${YELLOW}[4/6] Verifying K3s installation...${NC}"
kubectl wait --for=condition=Ready nodes --all --timeout=120s || {
    echo -e "${RED}K3s nodes not ready after 120s${NC}"
    exit 1
}

# Display node status
echo -e "${GREEN}K3s node status:${NC}"
kubectl get nodes -o wide

# Setup kubectl for non-root user
SUDO_USER=${SUDO_USER:-$(logname 2>/dev/null || echo "")}
if [ -n "$SUDO_USER" ] && [ "$SUDO_USER" != "root" ]; then
    echo -e "${YELLOW}[5/6] Setting up kubectl for user ${SUDO_USER}...${NC}"
    mkdir -p /home/$SUDO_USER/.kube
    cp /etc/rancher/k3s/k3s.yaml /home/$SUDO_USER/.kube/config
    chown -R $SUDO_USER:$SUDO_USER /home/$SUDO_USER/.kube
    chmod 600 /home/$SUDO_USER/.kube/config

    # Add kubectl completion
    if ! grep -q "kubectl completion bash" /home/$SUDO_USER/.bashrc; then
        echo "source <(kubectl completion bash)" >> /home/$SUDO_USER/.bashrc
        echo "alias k=kubectl" >> /home/$SUDO_USER/.bashrc
        echo "complete -F __start_kubectl k" >> /home/$SUDO_USER/.bashrc
    fi
fi

# Install Helm if not present
echo -e "${YELLOW}[6/6] Installing/Verifying Helm...${NC}"
if ! command -v helm &> /dev/null; then
    echo -e "${BLUE}Installing Helm...${NC}"
    curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
else
    echo -e "${GREEN}Helm already installed: $(helm version --short)${NC}"
fi

# Add Helm repositories
echo -e "${BLUE}Adding Helm repositories...${NC}"
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx 2>/dev/null || true
helm repo add jetstack https://charts.jetstack.io 2>/dev/null || true
helm repo add bitnami https://charts.bitnami.com/bitnami 2>/dev/null || true
helm repo add metallb https://metallb.github.io/metallb 2>/dev/null || true
helm repo update

# Save K3s token for worker nodes
echo -e "${BLUE}Saving K3s token...${NC}"
cat /var/lib/rancher/k3s/server/node-token > /etc/rancher/k3s/node-token
chmod 600 /etc/rancher/k3s/node-token

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}K3s Master Installation Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${YELLOW}Cluster Information:${NC}"
echo -e "  K3s Version: ${GREEN}$(k3s --version | head -n1)${NC}"
echo -e "  Container Runtime: ${GREEN}Docker${NC}"
echo -e "  Node IP: ${GREEN}${NODE_IP}${NC}"
echo -e "  API Server: ${GREEN}https://${NODE_IP}:6443${NC}"
echo ""
echo -e "${YELLOW}K3s Token (for worker nodes):${NC}"
echo -e "${GREEN}$(cat /etc/rancher/k3s/node-token)${NC}"
echo ""
echo -e "${YELLOW}Kubeconfig location:${NC}"
echo -e "  ${GREEN}/etc/rancher/k3s/k3s.yaml${NC}"
echo ""
echo -e "${YELLOW}Node Status:${NC}"
kubectl get nodes -o wide
echo ""
echo -e "${YELLOW}System Pods:${NC}"
kubectl get pods -A
echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Next Steps:${NC}"
echo -e "${BLUE}============================================${NC}"
echo -e "1. Install MetalLB: ${GREEN}sudo ./install-metallb.sh${NC}"
echo -e "2. Install NGINX Ingress: ${GREEN}sudo ./install-nginx-ingress.sh${NC}"
echo -e "3. Install Ceph CSI: ${GREEN}sudo ./install-ceph-csi.sh${NC}"
echo -e "4. Install Cert-Manager: ${GREEN}sudo ./install-cert-manager.sh${NC}"
echo ""
echo -e "Worker Node Join Command:"
echo -e "${GREEN}curl -sfL https://get.k3s.io | K3S_URL=https://${NODE_IP}:6443 K3S_TOKEN=$(cat /etc/rancher/k3s/node-token) sh -s - agent --docker${NC}"
echo ""
