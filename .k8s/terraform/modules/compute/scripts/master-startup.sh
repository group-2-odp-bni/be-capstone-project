#!/bin/bash
# K3s Master Node Startup Script
# This script runs on first boot to prepare the VM for K3s installation
# Actual K3s installation is done via the install-k3s-master.sh script

set -euo pipefail

# Logging
exec > >(tee -a /var/log/master-startup.log)
exec 2>&1

echo "=========================================="
echo "K3s Master Node Preparation"
echo "Starting at: $(date)"
echo "=========================================="

# Update system
echo "[1/5] Updating system packages..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get upgrade -y -qq
apt-get install -y -qq \
    curl \
    wget \
    git \
    htop \
    vim \
    jq \
    unzip \
    apt-transport-https \
    ca-certificates \
    software-properties-common \
    gnupg \
    lsb-release

# Install Docker (required for --docker flag in K3s)
echo "[2/5] Installing Docker..."
if ! command -v docker &> /dev/null; then
    # Add Docker's official GPG key
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg

    # Set up Docker repository
    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

    # Install Docker Engine
    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    # Configure Docker daemon
    cat > /etc/docker/daemon.json <<EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2"
}
EOF

    # Start and enable Docker
    systemctl enable docker
    systemctl start docker

    # Add user to docker group
    usermod -aG docker ${ssh_user} || true

    echo "Docker installed successfully"
else
    echo "Docker already installed"
fi

# Verify Docker installation
docker --version

# Configure system for Kubernetes
echo "[3/5] Configuring system for Kubernetes..."

# Disable swap (required for Kubernetes)
swapoff -a
sed -i '/ swap / s/^/#/' /etc/fstab

# Load required kernel modules
cat > /etc/modules-load.d/k3s.conf <<EOF
overlay
br_netfilter
EOF

modprobe overlay
modprobe br_netfilter

# Set required sysctl parameters
cat > /etc/sysctl.d/k3s.conf <<EOF
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
net.ipv6.conf.all.forwarding        = 1
EOF

sysctl --system

# Create K3s setup directory
echo "[4/5] Creating K3s setup directory..."
SETUP_DIR="/home/${ssh_user}/k3s-setup"
mkdir -p $SETUP_DIR
chown -R ${ssh_user}:${ssh_user} $SETUP_DIR

# Create K3s configuration directory
mkdir -p /etc/rancher/k3s

# Write K3s token to file (for worker node joining)
echo "${k3s_token}" > /etc/rancher/k3s/k3s-token
chmod 600 /etc/rancher/k3s/k3s-token

# Create environment file for K3s configuration
cat > $SETUP_DIR/.env <<EOF
# K3s Configuration
K3S_VERSION=${k3s_version}
K3S_TOKEN=${k3s_token}
METALLB_IP_RANGE_START=${metallb_ip_range_start}
METALLB_IP_RANGE_END=${metallb_ip_range_end}
SUBNET_CIDR=${subnet_cidr}
NODE_IP=$(hostname -I | awk '{print $1}')
EOF

chown ${ssh_user}:${ssh_user} $SETUP_DIR/.env
chmod 600 $SETUP_DIR/.env

# Create marker file to indicate preparation is complete
touch /var/log/master-prepared
echo "Master node preparation completed at: $(date)" >> /var/log/master-prepared

echo ""
echo "=========================================="
echo "Master Node Preparation Complete!"
echo "=========================================="
echo ""
echo "Node IP: $(hostname -I | awk '{print $1}')"
echo "Docker Version: $(docker --version)"
echo ""
echo "Next steps:"
echo "1. SSH to this machine"
echo "2. Run the K3s installation scripts from: $SETUP_DIR"
echo ""
echo "Setup directory created at: $SETUP_DIR"
echo "=========================================="
