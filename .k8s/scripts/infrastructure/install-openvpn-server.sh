#!/bin/bash
# OpenVPN Server Installation and Configuration
# Secure team access to database and Kubernetes infrastructure
# Run this script on the master node

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
OPENVPN_DIR="/etc/openvpn"
EASY_RSA_DIR="/etc/openvpn/easy-rsa"
SERVER_CONFIG="/etc/openvpn/server.conf"
VPN_SUBNET="10.8.0.0"
VPN_NETMASK="255.255.255.0"
VPN_PORT="1194"
VPN_PROTOCOL="udp"
K8S_CLUSTER_CIDR="10.43.0.0/16"
K8S_SERVICE_CIDR="10.43.0.0/16"
VM_SUBNET="10.0.1.0/24"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}OpenVPN Server Installation${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Please run as root or with sudo${NC}"
    exit 1
fi

# Check if already installed
if systemctl is-active --quiet openvpn@server; then
    echo -e "${YELLOW}OpenVPN server is already running${NC}"
    read -p "Do you want to reinstall? [y/N]: " reinstall
    if [[ ! "$reinstall" =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi
    systemctl stop openvpn@server
fi

echo -e "${YELLOW}Step 1/7: Installing OpenVPN and Easy-RSA...${NC}"
apt-get update -qq
apt-get install -y openvpn easy-rsa iptables-persistent

echo -e "${GREEN}✓ OpenVPN installed${NC}"
echo ""

echo -e "${YELLOW}Step 2/7: Setting up PKI infrastructure...${NC}"

# Setup Easy-RSA
mkdir -p $EASY_RSA_DIR
cp -r /usr/share/easy-rsa/* $EASY_RSA_DIR/
cd $EASY_RSA_DIR

# Initialize PKI
./easyrsa init-pki

# Create CA certificate
echo -e "${BLUE}Creating Certificate Authority...${NC}"
EASYRSA_BATCH=1 ./easyrsa build-ca nopass

# Generate server certificate
echo -e "${BLUE}Generating server certificate...${NC}"
EASYRSA_BATCH=1 ./easyrsa build-server-full server nopass

# Generate Diffie-Hellman parameters
echo -e "${BLUE}Generating DH parameters (this may take a while)...${NC}"
./easyrsa gen-dh

# Generate TLS auth key
openvpn --genkey secret $OPENVPN_DIR/ta.key

# Copy certificates to OpenVPN directory
cp pki/ca.crt $OPENVPN_DIR/
cp pki/issued/server.crt $OPENVPN_DIR/
cp pki/private/server.key $OPENVPN_DIR/
cp pki/dh.pem $OPENVPN_DIR/

echo -e "${GREEN}✓ PKI infrastructure ready${NC}"
echo ""

echo -e "${YELLOW}Step 3/7: Creating server configuration...${NC}"

cat > $SERVER_CONFIG <<EOF
# OpenVPN Server Configuration
# Orange Wallet Project - Development Environment

# Network settings
port $VPN_PORT
proto $VPN_PROTOCOL
dev tun

# SSL/TLS certificates
ca ca.crt
cert server.crt
key server.key
dh dh.pem
tls-auth ta.key 0

# Network topology
topology subnet
server $VPN_SUBNET $VPN_NETMASK

# Push routes to clients (without CIDR notation for OpenVPN 3.x compatibility)
push "route 10.0.1.0 255.255.255.0"     # VM subnet
push "route 10.43.0.0 255.255.0.0"     # Service CIDR (ClusterIP)
push "route 10.42.0.0 255.255.0.0"     # Pod CIDR (for headless services)

# Allow clients to communicate with each other
client-to-client

# Keepalive settings
keepalive 10 120

# Compression
compress lz4-v2
push "compress lz4-v2"

# Security settings
cipher AES-256-GCM
auth SHA256
tls-version-min 1.2
tls-cipher TLS-ECDHE-ECDSA-WITH-AES-256-GCM-SHA384:TLS-ECDHE-RSA-WITH-AES-256-GCM-SHA384

# User/group
user nobody
group nogroup

# Persistence
persist-key
persist-tun

# Logging
status /var/log/openvpn/openvpn-status.log
log-append /var/log/openvpn/openvpn.log
verb 3
mute 20

# Client configuration
client-config-dir /etc/openvpn/ccd
EOF

# Create directories
mkdir -p /var/log/openvpn
mkdir -p /etc/openvpn/ccd

echo -e "${GREEN}✓ Server configuration created${NC}"
echo ""

echo -e "${YELLOW}Step 4/7: Configuring IP forwarding and NAT...${NC}"

# Enable IP forwarding
sed -i 's/#net.ipv4.ip_forward=1/net.ipv4.ip_forward=1/' /etc/sysctl.conf
sysctl -p

# Get primary network interface
PRIMARY_IFACE=$(ip route | grep default | awk '{print $5}')

# Setup iptables rules for NAT
iptables -t nat -A POSTROUTING -s $VPN_SUBNET/24 -o $PRIMARY_IFACE -j MASQUERADE

# Allow VPN traffic
iptables -A INPUT -i tun+ -j ACCEPT
iptables -A FORWARD -i tun+ -j ACCEPT
iptables -A FORWARD -i $PRIMARY_IFACE -o tun+ -j ACCEPT
iptables -A FORWARD -i tun+ -o $PRIMARY_IFACE -j ACCEPT

# Save iptables rules
netfilter-persistent save

echo -e "${GREEN}✓ NAT and forwarding configured${NC}"
echo ""

echo -e "${YELLOW}Step 5/7: Starting OpenVPN server...${NC}"

# Enable and start OpenVPN
systemctl enable openvpn@server
systemctl start openvpn@server

# Wait for service to start
sleep 3

if systemctl is-active --quiet openvpn@server; then
    echo -e "${GREEN}✓ OpenVPN server is running${NC}"
else
    echo -e "${RED}✗ OpenVPN server failed to start${NC}"
    echo -e "${YELLOW}Check logs: journalctl -u openvpn@server -n 50${NC}"
    exit 1
fi

echo ""

echo -e "${YELLOW}Step 6/7: Creating GCP firewall rule...${NC}"

# Get external IP
EXTERNAL_IP=$(curl -s http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip -H "Metadata-Flavor: Google")

echo -e "${BLUE}Creating firewall rule for OpenVPN...${NC}"

gcloud compute firewall-rules create allow-openvpn \
    --network=orange-wallet-dev-vpc \
    --allow=udp:1194 \
    --source-ranges=0.0.0.0/0 \
    --description="Allow OpenVPN connections" \
    --project=orange-wallet-project 2>/dev/null || echo -e "${YELLOW}Firewall rule already exists${NC}"

echo -e "${GREEN}✓ Firewall configured${NC}"
echo ""

echo -e "${YELLOW}Step 7/7: Creating client management scripts...${NC}"

# Create helper script location
SCRIPTS_DIR="/root/openvpn-scripts"
mkdir -p $SCRIPTS_DIR

# Create client generation script
cat > $SCRIPTS_DIR/create-client.sh <<'SCRIPT_EOF'
#!/bin/bash
# Generate OpenVPN client configuration

set -e

if [ -z "$1" ]; then
    echo "Usage: $0 <client-name>"
    exit 1
fi

CLIENT_NAME=$1
EASY_RSA_DIR="/etc/openvpn/easy-rsa"
OPENVPN_DIR="/etc/openvpn"
OUTPUT_DIR="/root/openvpn-clients"

mkdir -p $OUTPUT_DIR

cd $EASY_RSA_DIR

# Generate client certificate
echo "Generating certificate for $CLIENT_NAME..."
EASYRSA_BATCH=1 ./easyrsa build-client-full $CLIENT_NAME nopass

# Get server external IP
SERVER_IP=$(curl -s http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip -H "Metadata-Flavor: Google")

# Create .ovpn file
cat > $OUTPUT_DIR/${CLIENT_NAME}.ovpn <<EOF
client
dev tun
proto udp
remote $SERVER_IP 1194
resolv-retry infinite
nobind
persist-key
persist-tun
remote-cert-tls server
auth-user-pass
cipher AES-256-GCM
auth SHA256
compress lz4-v2
verb 3
key-direction 1

<ca>
$(cat $OPENVPN_DIR/ca.crt)
</ca>

<cert>
$(cat $EASY_RSA_DIR/pki/issued/${CLIENT_NAME}.crt)
</cert>

<key>
$(cat $EASY_RSA_DIR/pki/private/${CLIENT_NAME}.key)
</key>

<tls-auth>
$(cat $OPENVPN_DIR/ta.key)
</tls-auth>
EOF

echo ""
echo "✓ Client configuration created: $OUTPUT_DIR/${CLIENT_NAME}.ovpn"
echo ""
echo "IMPORTANT: Dual Authentication Required"
echo "  1. Certificate: Embedded in .ovpn file"
echo "  2. Username/Password: Linux system user credentials"
echo ""
echo "Create Linux user for VPN access:"
echo "  sudo adduser $CLIENT_NAME"
echo ""
echo "Send .ovpn file securely to user."
SCRIPT_EOF

chmod +x $SCRIPTS_DIR/create-client.sh

# Create revocation script
cat > $SCRIPTS_DIR/revoke-client.sh <<'SCRIPT_EOF'
#!/bin/bash
# Revoke OpenVPN client certificate

set -e

if [ -z "$1" ]; then
    echo "Usage: $0 <client-name>"
    exit 1
fi

CLIENT_NAME=$1
EASY_RSA_DIR="/etc/openvpn/easy-rsa"

cd $EASY_RSA_DIR

# Revoke certificate
EASYRSA_BATCH=1 ./easyrsa revoke $CLIENT_NAME

# Generate CRL
./easyrsa gen-crl

# Copy CRL to OpenVPN directory
cp pki/crl.pem /etc/openvpn/

# Update server config to use CRL
if ! grep -q "crl-verify" /etc/openvpn/server.conf; then
    echo "crl-verify crl.pem" >> /etc/openvpn/server.conf
    systemctl restart openvpn@server
fi

echo ""
echo "✓ Client certificate revoked: $CLIENT_NAME"
echo "✓ OpenVPN server restarted"
SCRIPT_EOF

chmod +x $SCRIPTS_DIR/revoke-client.sh

echo -e "${GREEN}✓ Client management scripts created${NC}"
echo ""

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}OpenVPN Server Installation Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${BLUE}Server Information:${NC}"
echo -e "  External IP: ${GREEN}$EXTERNAL_IP${NC}"
echo -e "  VPN Port: ${GREEN}$VPN_PORT${NC}"
echo -e "  Protocol: ${GREEN}$VPN_PROTOCOL${NC}"
echo -e "  VPN Subnet: ${GREEN}$VPN_SUBNET/24${NC}"
echo ""
echo -e "${BLUE}Access to Resources via VPN:${NC}"
echo -e "  PostgreSQL: ${GREEN}postgres.orange-wallet.svc.cluster.local:5432${NC}"
echo -e "  Redis: ${GREEN}redis.orange-wallet.svc.cluster.local:6379${NC}"
echo -e "  Kafka: ${GREEN}kafka.orange-wallet.svc.cluster.local:9092${NC}"
echo -e "  Kubernetes API: ${GREEN}https://10.0.1.4:6443${NC}"
echo ""
echo -e "${BLUE}Client Management:${NC}"
echo -e "  Create client: ${GREEN}$SCRIPTS_DIR/create-client.sh <name>${NC}"
echo -e "  Revoke client: ${GREEN}$SCRIPTS_DIR/revoke-client.sh <name>${NC}"
echo -e "  Client configs: ${GREEN}/root/openvpn-clients/${NC}"
echo ""
echo -e "${BLUE}Service Status:${NC}"
echo -e "  Status: ${GREEN}systemctl status openvpn@server${NC}"
echo -e "  Logs: ${GREEN}journalctl -u openvpn@server -f${NC}"
echo -e "  Connected clients: ${GREEN}cat /var/log/openvpn/openvpn-status.log${NC}"
echo ""
echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}Next Steps:${NC}"
echo -e "${YELLOW}============================================${NC}"
echo -e "1. Create client certificate:"
echo -e "   ${GREEN}sudo $SCRIPTS_DIR/create-client.sh developer1${NC}"
echo ""
echo -e "2. Create Linux user for authentication:"
echo -e "   ${GREEN}sudo adduser developer1${NC}"
echo -e "   ${YELLOW}(User will be prompted for username/password when connecting)${NC}"
echo ""
echo -e "3. Download .ovpn file from ${GREEN}/root/openvpn-clients/${NC}"
echo ""
echo -e "4. Share .ovpn file securely with team member"
echo ""
echo -e "5. Team member imports .ovpn into OpenVPN client:"
echo -e "   - Windows: https://openvpn.net/client/"
echo -e "   - macOS: Tunnelblick or OpenVPN Connect"
echo -e "   - Linux: ${GREEN}sudo apt install openvpn${NC}"
echo ""
echo -e "6. Connect using .ovpn file + Linux credentials"
echo ""
echo -e "${BLUE}Database Connection via VPN:${NC}"
echo -e "After connecting to VPN, access database using:"
echo -e "  Host: ${GREEN}postgres.orange-wallet.svc.cluster.local${NC}"
echo -e "  Port: ${GREEN}5432${NC}"
echo -e "  Database: ${GREEN}orange_db${NC}"
echo -e "  Username: ${GREEN}<from secrets>${NC}"
echo -e "  Password: ${GREEN}<from secrets>${NC}"
echo ""
echo -e "${RED}Security Note:${NC}"
echo -e "${YELLOW}⚠ Keep .ovpn files secure (contains certificates)${NC}"
echo -e "${YELLOW}⚠ Revoke access immediately when team members leave${NC}"
echo -e "${YELLOW}⚠ Monitor connected clients regularly${NC}"
echo ""
