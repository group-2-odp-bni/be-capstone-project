#!/bin/bash
# OpenVPN Client Management Interface
# Interactive script for managing OpenVPN clients

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

EASY_RSA_DIR="/etc/openvpn/easy-rsa"
OPENVPN_DIR="/etc/openvpn"
OUTPUT_DIR="/root/openvpn-clients"
SCRIPTS_DIR="/root/openvpn-scripts"

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Please run as root or with sudo${NC}"
    exit 1
fi

# Check if OpenVPN is installed
if ! systemctl is-active --quiet openvpn@server; then
    echo -e "${RED}OpenVPN server is not running${NC}"
    echo -e "${YELLOW}Please run install-openvpn-server.sh first${NC}"
    exit 1
fi

show_menu() {
    clear
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}OpenVPN Client Management${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""
    echo -e "1. ${GREEN}Create New Client${NC}"
    echo -e "2. ${YELLOW}List All Clients${NC}"
    echo -e "3. ${YELLOW}Show Connected Clients${NC}"
    echo -e "4. ${RED}Revoke Client Access${NC}"
    echo -e "5. ${BLUE}Download Client Config${NC}"
    echo -e "6. ${BLUE}Show Database Connection Info${NC}"
    echo -e "7. ${BLUE}Server Status${NC}"
    echo -e "8. ${RED}Exit${NC}"
    echo ""
    read -p "Enter choice [1-8]: " choice
}

create_client() {
    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}Create New Client${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""

    read -p "Enter client name (e.g., developer1, designer-maria): " client_name

    if [ -z "$client_name" ]; then
        echo -e "${RED}Client name cannot be empty${NC}"
        read -p "Press Enter to continue..."
        return
    fi

    # Check if client already exists
    if [ -f "$EASY_RSA_DIR/pki/issued/${client_name}.crt" ]; then
        echo -e "${YELLOW}Client '$client_name' already exists${NC}"
        read -p "Press Enter to continue..."
        return
    fi

    echo ""
    echo -e "${YELLOW}Creating client certificate...${NC}"

    mkdir -p $OUTPUT_DIR
    cd $EASY_RSA_DIR

    # Generate client certificate
    EASYRSA_BATCH=1 ./easyrsa build-client-full $client_name nopass

    # Get server external IP
    SERVER_IP=$(curl -s http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip -H "Metadata-Flavor: Google")

    # Get database credentials
    DB_NAME=$(kubectl get secret app-secrets -n orange-wallet -o jsonpath='{.data.DB_NAME}' 2>/dev/null | base64 -d)
    DB_USERNAME=$(kubectl get secret app-secrets -n orange-wallet -o jsonpath='{.data.DB_USERNAME}' 2>/dev/null | base64 -d)
    DB_PASSWORD=$(kubectl get secret app-secrets -n orange-wallet -o jsonpath='{.data.DB_PASSWORD}' 2>/dev/null | base64 -d)

    # Create .ovpn file
    cat > $OUTPUT_DIR/${client_name}.ovpn <<EOF
# OpenVPN Client Configuration
# Orange Wallet Project - ${client_name}
# Generated: $(date)

client
dev tun
proto udp
remote $SERVER_IP 1194
resolv-retry infinite
nobind
persist-key
persist-tun
remote-cert-tls server
cipher AES-256-GCM
auth SHA256
compress lz4-v2
verb 3
key-direction 1

<ca>
$(cat $OPENVPN_DIR/ca.crt)
</ca>

<cert>
$(cat $EASY_RSA_DIR/pki/issued/${client_name}.crt)
</cert>

<key>
$(cat $EASY_RSA_DIR/pki/private/${client_name}.key)
</key>

<tls-auth>
$(cat $OPENVPN_DIR/ta.key)
</tls-auth>
EOF

    # Create instruction file
    cat > $OUTPUT_DIR/${client_name}-instructions.txt <<EOF
========================================
OpenVPN Access Instructions
========================================
Client: ${client_name}
Generated: $(date)

========================================
1. Install OpenVPN Client
========================================

Windows:
  Download from: https://openvpn.net/client/

macOS:
  Install Tunnelblick: https://tunnelblick.net/
  Or OpenVPN Connect: https://openvpn.net/client/

Linux:
  sudo apt install openvpn
  sudo openvpn --config ${client_name}.ovpn

========================================
2. Import Configuration
========================================

- Import the ${client_name}.ovpn file into your OpenVPN client
- Connect to the VPN
- Wait for "Connected" status

========================================
3. Access Database
========================================

Once connected to VPN, use these credentials:

Host: postgres.orange-wallet.svc.cluster.local
Port: 5432
Database: ${DB_NAME}
Username: ${DB_USERNAME}
Password: ${DB_PASSWORD}

JDBC URL:
jdbc:postgresql://postgres.orange-wallet.svc.cluster.local:5432/${DB_NAME}

========================================
4. Other Services (via VPN)
========================================

Redis:
  redis.orange-wallet.svc.cluster.local:6379

Kafka:
  kafka.orange-wallet.svc.cluster.local:9092

Kubernetes API:
  https://10.0.1.4:6443

========================================
5. Troubleshooting
========================================

Cannot resolve postgres.orange-wallet.svc.cluster.local:
  - Try using ClusterIP instead: kubectl get svc postgres -n orange-wallet
  - Use the ClusterIP directly (e.g., 10.43.x.x:5432)

Connection timeout:
  - Check VPN is connected
  - Verify firewall allows VPN traffic

========================================
Security Notes
========================================

⚠ Keep this .ovpn file secure
⚠ Do not share with others
⚠ Report immediately if file is compromised
⚠ Disconnect VPN when not in use

========================================
Support
========================================

If you need help, contact the infrastructure team.
EOF

    echo -e "${GREEN}✓ Client created successfully${NC}"
    echo ""
    echo -e "${BLUE}Files created:${NC}"
    echo -e "  Config: ${GREEN}$OUTPUT_DIR/${client_name}.ovpn${NC}"
    echo -e "  Instructions: ${GREEN}$OUTPUT_DIR/${client_name}-instructions.txt${NC}"
    echo ""
    echo -e "${YELLOW}Download these files and send securely to ${client_name}${NC}"
    echo ""

    read -p "Press Enter to continue..."
}

list_clients() {
    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}All Registered Clients${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""

    if [ ! -d "$EASY_RSA_DIR/pki/issued" ]; then
        echo -e "${YELLOW}No clients found${NC}"
        read -p "Press Enter to continue..."
        return
    fi

    echo -e "${GREEN}Active Clients:${NC}"
    for cert in $EASY_RSA_DIR/pki/issued/*.crt; do
        if [ "$cert" != "$EASY_RSA_DIR/pki/issued/server.crt" ]; then
            client=$(basename "$cert" .crt)
            issued_date=$(openssl x509 -in "$cert" -noout -startdate | cut -d= -f2)
            expiry_date=$(openssl x509 -in "$cert" -noout -enddate | cut -d= -f2)

            echo ""
            echo -e "  Client: ${BLUE}$client${NC}"
            echo -e "    Issued: $issued_date"
            echo -e "    Expires: $expiry_date"

            # Check if revoked
            if [ -f "$EASY_RSA_DIR/pki/revoked/certs_by_serial/${client}.crt" ] || \
               [ -f "$EASY_RSA_DIR/pki/revoked/certs_by_serial/$(openssl x509 -in "$cert" -noout -serial | cut -d= -f2).crt" ]; then
                echo -e "    Status: ${RED}REVOKED${NC}"
            else
                echo -e "    Status: ${GREEN}ACTIVE${NC}"
            fi
        fi
    done

    echo ""
    read -p "Press Enter to continue..."
}

show_connected_clients() {
    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}Currently Connected Clients${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""

    if [ ! -f "/var/log/openvpn/openvpn-status.log" ]; then
        echo -e "${YELLOW}Status log not found${NC}"
        read -p "Press Enter to continue..."
        return
    fi

    # Show connected clients
    echo -e "${GREEN}Connected Clients:${NC}"
    echo ""

    awk '/CLIENT_LIST/ {if (NR>2) print}' /var/log/openvpn/openvpn-status.log | while read line; do
        if [ ! -z "$line" ]; then
            client_name=$(echo $line | awk '{print $2}')
            real_ip=$(echo $line | awk '{print $3}')
            vpn_ip=$(echo $line | awk '{print $4}')
            connected_since=$(echo $line | awk '{print $8" "$9}')

            echo -e "  ${BLUE}$client_name${NC}"
            echo -e "    VPN IP: $vpn_ip"
            echo -e "    Real IP: $real_ip"
            echo -e "    Connected: $connected_since"
            echo ""
        fi
    done

    total_clients=$(awk '/CLIENT_LIST/ {if (NR>2) print}' /var/log/openvpn/openvpn-status.log | wc -l)
    echo -e "${GREEN}Total connected: $total_clients${NC}"
    echo ""

    read -p "Press Enter to continue..."
}

revoke_client() {
    echo ""
    echo -e "${RED}============================================${NC}"
    echo -e "${RED}Revoke Client Access${NC}"
    echo -e "${RED}============================================${NC}"
    echo ""

    read -p "Enter client name to revoke: " client_name

    if [ -z "$client_name" ]; then
        echo -e "${RED}Client name cannot be empty${NC}"
        read -p "Press Enter to continue..."
        return
    fi

    # Check if client exists
    if [ ! -f "$EASY_RSA_DIR/pki/issued/${client_name}.crt" ]; then
        echo -e "${RED}Client '$client_name' not found${NC}"
        read -p "Press Enter to continue..."
        return
    fi

    echo ""
    echo -e "${RED}⚠ WARNING: This will immediately revoke access for '$client_name'${NC}"
    read -p "Are you sure? [y/N]: " confirm

    if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
        echo "Cancelled."
        read -p "Press Enter to continue..."
        return
    fi

    echo ""
    echo -e "${YELLOW}Revoking certificate...${NC}"

    cd $EASY_RSA_DIR

    # Revoke certificate
    EASYRSA_BATCH=1 ./easyrsa revoke $client_name

    # Generate CRL
    ./easyrsa gen-crl

    # Copy CRL to OpenVPN directory
    cp pki/crl.pem /etc/openvpn/

    # Update server config to use CRL
    if ! grep -q "crl-verify" /etc/openvpn/server.conf; then
        echo "crl-verify crl.pem" >> /etc/openvpn/server.conf
    fi

    # Restart server to apply changes
    systemctl restart openvpn@server

    echo -e "${GREEN}✓ Client '$client_name' has been revoked${NC}"
    echo -e "${GREEN}✓ OpenVPN server restarted${NC}"
    echo ""

    read -p "Press Enter to continue..."
}

download_client_info() {
    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}Download Client Configuration${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""

    read -p "Enter client name: " client_name

    if [ -z "$client_name" ]; then
        echo -e "${RED}Client name cannot be empty${NC}"
        read -p "Press Enter to continue..."
        return
    fi

    if [ ! -f "$OUTPUT_DIR/${client_name}.ovpn" ]; then
        echo -e "${RED}Configuration for '$client_name' not found${NC}"
        echo -e "${YELLOW}Create the client first${NC}"
        read -p "Press Enter to continue..."
        return
    fi

    echo ""
    echo -e "${GREEN}Client files location:${NC}"
    echo -e "  Config: ${BLUE}$OUTPUT_DIR/${client_name}.ovpn${NC}"
    echo -e "  Instructions: ${BLUE}$OUTPUT_DIR/${client_name}-instructions.txt${NC}"
    echo ""
    echo -e "${YELLOW}Download these files using:${NC}"
    echo -e "  ${GREEN}scp root@VM_IP:$OUTPUT_DIR/${client_name}.* .${NC}"
    echo ""
    echo -e "Or use gcloud:"
    echo -e "  ${GREEN}gcloud compute scp orange-wallet-dev-master:$OUTPUT_DIR/${client_name}.ovpn . --zone=asia-southeast2-a${NC}"
    echo ""

    read -p "Press Enter to continue..."
}

show_db_info() {
    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}Database Connection Information${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""

    # Get database credentials
    DB_NAME=$(kubectl get secret app-secrets -n orange-wallet -o jsonpath='{.data.DB_NAME}' 2>/dev/null | base64 -d)
    DB_USERNAME=$(kubectl get secret app-secrets -n orange-wallet -o jsonpath='{.data.DB_USERNAME}' 2>/dev/null | base64 -d)
    DB_PASSWORD=$(kubectl get secret app-secrets -n orange-wallet -o jsonpath='{.data.DB_PASSWORD}' 2>/dev/null | base64 -d)
    POSTGRES_IP=$(kubectl get svc postgres -n orange-wallet -o jsonpath='{.spec.clusterIP}')

    echo -e "${GREEN}After connecting to VPN, use:${NC}"
    echo ""
    echo -e "${BLUE}Connection Details:${NC}"
    echo -e "  Host: ${GREEN}postgres.orange-wallet.svc.cluster.local${NC}"
    echo -e "  Alternative: ${GREEN}$POSTGRES_IP${NC}"
    echo -e "  Port: ${GREEN}5432${NC}"
    echo -e "  Database: ${GREEN}$DB_NAME${NC}"
    echo -e "  Username: ${GREEN}$DB_USERNAME${NC}"
    echo -e "  Password: ${GREEN}$DB_PASSWORD${NC}"
    echo ""
    echo -e "${BLUE}JDBC URL:${NC}"
    echo -e "  ${GREEN}jdbc:postgresql://postgres.orange-wallet.svc.cluster.local:5432/$DB_NAME${NC}"
    echo ""
    echo -e "${BLUE}psql Command:${NC}"
    echo -e "  ${GREEN}psql -h postgres.orange-wallet.svc.cluster.local -p 5432 -U $DB_USERNAME -d $DB_NAME${NC}"
    echo ""

    read -p "Press Enter to continue..."
}

show_server_status() {
    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}OpenVPN Server Status${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""

    # Service status
    if systemctl is-active --quiet openvpn@server; then
        echo -e "Service Status: ${GREEN}RUNNING${NC}"
    else
        echo -e "Service Status: ${RED}STOPPED${NC}"
    fi

    # Port check
    if netstat -ln | grep -q ":1194 "; then
        echo -e "Port 1194: ${GREEN}LISTENING${NC}"
    else
        echo -e "Port 1194: ${RED}NOT LISTENING${NC}"
    fi

    # Get external IP
    SERVER_IP=$(curl -s http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip -H "Metadata-Flavor: Google")
    echo -e "External IP: ${GREEN}$SERVER_IP${NC}"

    # Connected clients count
    if [ -f "/var/log/openvpn/openvpn-status.log" ]; then
        total_clients=$(awk '/CLIENT_LIST/ {if (NR>2) print}' /var/log/openvpn/openvpn-status.log | wc -l)
        echo -e "Connected Clients: ${GREEN}$total_clients${NC}"
    fi

    echo ""
    echo -e "${BLUE}Recent Logs:${NC}"
    journalctl -u openvpn@server -n 10 --no-pager

    echo ""
    read -p "Press Enter to continue..."
}

# Main loop
while true; do
    show_menu

    case $choice in
        1)
            create_client
            ;;
        2)
            list_clients
            ;;
        3)
            show_connected_clients
            ;;
        4)
            revoke_client
            ;;
        5)
            download_client_info
            ;;
        6)
            show_db_info
            ;;
        7)
            show_server_status
            ;;
        8)
            echo ""
            echo -e "${GREEN}Goodbye!${NC}"
            exit 0
            ;;
        *)
            echo -e "${RED}Invalid choice${NC}"
            sleep 1
            ;;
    esac
done
