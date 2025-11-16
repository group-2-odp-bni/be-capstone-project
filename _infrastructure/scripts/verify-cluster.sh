#!/bin/bash
# =============================================================================
# Cluster Verification Script
# =============================================================================
# Purpose: Verify cluster health and configuration
# Usage: ./verify-cluster.sh
# =============================================================================

set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KUBECONFIG="${SCRIPT_DIR}/../kubeconfig"

if [ ! -f "$KUBECONFIG" ]; then
    echo -e "${RED}ERROR: Kubeconfig not found at ${KUBECONFIG}${NC}"
    echo "Run Ansible playbook 03-configure-cluster.yml first"
    exit 1
fi

export KUBECONFIG

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Cluster Verification${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# -----------------------------------------------------------------------------
# Test 1: Cluster Connectivity
# -----------------------------------------------------------------------------
echo -e "${BLUE}1. Testing cluster connectivity...${NC}"
if kubectl cluster-info >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} Cluster is reachable"
else
    echo -e "${RED}✗${NC} Cannot connect to cluster"
    exit 1
fi

# -----------------------------------------------------------------------------
# Test 2: Node Status
# -----------------------------------------------------------------------------
echo ""
echo -e "${BLUE}2. Checking node status...${NC}"
kubectl get nodes

NOT_READY=$(kubectl get nodes --no-headers | grep -v "Ready" | wc -l)
if [ "$NOT_READY" -eq 0 ]; then
    echo -e "${GREEN}✓${NC} All nodes are Ready"
else
    echo -e "${YELLOW}⚠${NC} Some nodes are not Ready"
fi

# -----------------------------------------------------------------------------
# Test 3: Node Labels
# -----------------------------------------------------------------------------
echo ""
echo -e "${BLUE}3. Verifying node labels...${NC}"
kubectl get nodes --show-labels | grep "workload-type"

LABELED_NODES=$(kubectl get nodes -l workload-type --no-headers | wc -l)
TOTAL_NODES=$(kubectl get nodes --no-headers | wc -l)

if [ "$LABELED_NODES" -eq "$TOTAL_NODES" ]; then
    echo -e "${GREEN}✓${NC} All nodes have workload-type labels"
else
    echo -e "${YELLOW}⚠${NC} Some nodes missing workload-type labels"
fi

# -----------------------------------------------------------------------------
# Test 4: Node Taints
# -----------------------------------------------------------------------------
echo ""
echo -e "${BLUE}4. Verifying node taints...${NC}"
TAINTED_NODES=$(kubectl get nodes -o json | jq -r '.items[] | select(.spec.taints != null) | .metadata.name' | wc -l)

if [ "$TAINTED_NODES" -gt 0 ]; then
    echo -e "${GREEN}✓${NC} Stateful node taint found"
    kubectl get nodes -o custom-columns=NAME:.metadata.name,TAINTS:.spec.taints
else
    echo -e "${YELLOW}⚠${NC} No node taints configured"
fi

# -----------------------------------------------------------------------------
# Test 5: System Pods
# -----------------------------------------------------------------------------
echo ""
echo -e "${BLUE}5. Checking system pods...${NC}"
kubectl get pods -n kube-system

PENDING_PODS=$(kubectl get pods -n kube-system --no-headers | grep -v "Running\|Completed" | wc -l)
if [ "$PENDING_PODS" -eq 0 ]; then
    echo -e "${GREEN}✓${NC} All system pods are running"
else
    echo -e "${YELLOW}⚠${NC} Some system pods are not running"
fi

# -----------------------------------------------------------------------------
# Test 6: Namespaces
# -----------------------------------------------------------------------------
echo ""
echo -e "${BLUE}6. Checking namespaces...${NC}"
kubectl get namespaces

EXPECTED_NS=("argocd" "nginx-ingress" "cert-manager" "external-secrets" "observability" "database" "messaging")
MISSING_NS=0

for ns in "${EXPECTED_NS[@]}"; do
    if kubectl get namespace "$ns" >/dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Namespace: $ns"
    else
        echo -e "${RED}✗${NC} Missing namespace: $ns"
        MISSING_NS=$((MISSING_NS + 1))
    fi
done

if [ "$MISSING_NS" -eq 0 ]; then
    echo -e "${GREEN}✓${NC} All expected namespaces exist"
fi

# -----------------------------------------------------------------------------
# Test 7: Resource Quotas
# -----------------------------------------------------------------------------
echo ""
echo -e "${BLUE}7. Checking resource quotas...${NC}"
kubectl get resourcequotas -A

# -----------------------------------------------------------------------------
# Test 8: Priority Classes
# -----------------------------------------------------------------------------
echo ""
echo -e "${BLUE}8. Checking priority classes...${NC}"
kubectl get priorityclasses

# -----------------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------------
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Verification Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

TOTAL_NODES=$(kubectl get nodes --no-headers | wc -l)
READY_NODES=$(kubectl get nodes --no-headers | grep " Ready " | wc -l)
TOTAL_PODS=$(kubectl get pods -A --no-headers | wc -l)
RUNNING_PODS=$(kubectl get pods -A --no-headers | grep "Running" | wc -l)

echo "Nodes:   ${READY_NODES}/${TOTAL_NODES} Ready"
echo "Pods:    ${RUNNING_PODS}/${TOTAL_PODS} Running"
echo "Namespaces: $(kubectl get namespaces --no-headers | wc -l)"

echo ""
echo -e "${GREEN}Cluster verification complete!${NC}"
echo ""
echo "Kubeconfig: ${KUBECONFIG}"
echo ""
echo "Access cluster:"
echo "  export KUBECONFIG=${KUBECONFIG}"
echo "  kubectl get all -A"
