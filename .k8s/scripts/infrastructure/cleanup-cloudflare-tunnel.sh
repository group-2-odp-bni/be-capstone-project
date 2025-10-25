#!/bin/bash
# Cleanup Cloudflare Tunnel
# Removes tunnel from Kubernetes and optionally from Cloudflare

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

NAMESPACE="${NAMESPACE:-orange-wallet}"
TUNNEL_NAME="orange-wallet"

echo -e "${RED}============================================${NC}"
echo -e "${RED}Cloudflare Tunnel Cleanup${NC}"
echo -e "${RED}============================================${NC}"
echo ""

echo -e "${YELLOW}This will remove the Cloudflare Tunnel from your cluster.${NC}"
echo ""

read -p "Are you sure you want to proceed? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo -e "${GREEN}Cleanup cancelled${NC}"
    exit 0
fi

echo ""
echo -e "${BLUE}[1/3] Removing Kubernetes resources...${NC}"

# Delete DaemonSet
if kubectl get daemonset cloudflared -n $NAMESPACE &>/dev/null; then
    kubectl delete daemonset cloudflared -n $NAMESPACE
    echo -e "${GREEN}✓ DaemonSet deleted${NC}"
fi

# Delete Service
if kubectl get svc cloudflared-metrics -n $NAMESPACE &>/dev/null; then
    kubectl delete svc cloudflared-metrics -n $NAMESPACE
    echo -e "${GREEN}✓ Service deleted${NC}"
fi

# Delete ConfigMap
if kubectl get configmap cloudflared-config -n $NAMESPACE &>/dev/null; then
    kubectl delete configmap cloudflared-config -n $NAMESPACE
    echo -e "${GREEN}✓ ConfigMap deleted${NC}"
fi

# Delete Secret
if kubectl get secret cloudflared-credentials -n $NAMESPACE &>/dev/null; then
    kubectl delete secret cloudflared-credentials -n $NAMESPACE
    echo -e "${GREEN}✓ Secret deleted${NC}"
fi

echo ""
echo -e "${BLUE}[2/3] Verifying cleanup...${NC}"

# Check if resources are deleted
REMAINING=$(kubectl get all -n $NAMESPACE -l app=cloudflared -o name 2>/dev/null | wc -l)

if [ "$REMAINING" -eq 0 ]; then
    echo -e "${GREEN}✓ All Kubernetes resources removed${NC}"
else
    echo -e "${YELLOW}⚠ Some resources still exist:${NC}"
    kubectl get all -n $NAMESPACE -l app=cloudflared
fi

echo ""
echo -e "${BLUE}[3/3] Cloudflare Tunnel Management${NC}"
echo ""

if command -v cloudflared &> /dev/null; then
    echo -e "${YELLOW}Do you want to delete the tunnel from Cloudflare? (This will also remove DNS routes)${NC}"
    read -p "Delete tunnel from Cloudflare? (yes/no): " delete_tunnel

    if [ "$delete_tunnel" = "yes" ]; then
        echo -e "${YELLOW}Deleting tunnel: $TUNNEL_NAME${NC}"

        # Delete DNS routes first
        echo -e "${YELLOW}Removing DNS routes...${NC}"
        cloudflared tunnel route dns delete orangebybni.my.id api.orangebybni.my.id 2>/dev/null || true
        cloudflared tunnel route dns delete orangebybni.my.id auth.orangebybni.my.id 2>/dev/null || true
        cloudflared tunnel route dns delete orangebybni.my.id wallet.orangebybni.my.id 2>/dev/null || true

        # Delete tunnel
        cloudflared tunnel delete $TUNNEL_NAME

        echo -e "${GREEN}✓ Tunnel deleted from Cloudflare${NC}"

        # Remove local credentials
        TUNNEL_ID=$(cloudflared tunnel list 2>/dev/null | grep "$TUNNEL_NAME" | awk '{print $1}' || echo "")
        if [ -n "$TUNNEL_ID" ]; then
            CRED_FILE="$HOME/.cloudflared/${TUNNEL_ID}.json"
            if [ -f "$CRED_FILE" ]; then
                rm -f "$CRED_FILE"
                echo -e "${GREEN}✓ Local credentials removed${NC}"
            fi
        fi
    else
        echo -e "${YELLOW}Tunnel kept in Cloudflare${NC}"
        echo -e "${YELLOW}You can manually delete it later with:${NC}"
        echo -e "  ${GREEN}cloudflared tunnel delete $TUNNEL_NAME${NC}"
    fi
else
    echo -e "${YELLOW}cloudflared not installed locally${NC}"
    echo -e "${YELLOW}If you want to delete the tunnel from Cloudflare:${NC}"
    echo "1. Install cloudflared"
    echo "2. Run: cloudflared tunnel login"
    echo "3. Run: cloudflared tunnel delete $TUNNEL_NAME"
fi

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Cleanup Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

echo -e "${YELLOW}Summary:${NC}"
echo "- Kubernetes resources removed from namespace: $NAMESPACE"
if [ "$delete_tunnel" = "yes" ]; then
    echo "- Tunnel deleted from Cloudflare"
    echo "- DNS routes removed"
else
    echo "- Tunnel still exists in Cloudflare (not deleted)"
fi
echo ""

echo -e "${YELLOW}To redeploy the tunnel:${NC}"
echo "1. Run: ./setup-cloudflare-tunnel.sh"
echo "2. Run: ./deploy-cloudflare-tunnel.sh"
echo ""
