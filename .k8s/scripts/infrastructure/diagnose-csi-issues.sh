#!/bin/bash
# Diagnose CSI Driver Issues
# Comprehensive diagnostic tool for GCP PD CSI Driver problems

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

NAMESPACE="gce-pd-csi-driver"

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}CSI Driver Diagnostic Tool${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Check 1: Cluster access
echo -e "${YELLOW}[1/8] Checking cluster access...${NC}"
if kubectl get nodes &>/dev/null; then
    NODE_COUNT=$(kubectl get nodes --no-headers | wc -l)
    echo -e "${GREEN}✓ Cluster accessible ($NODE_COUNT nodes)${NC}"
else
    echo -e "${RED}✗ Cannot access cluster${NC}"
    exit 1
fi
echo ""

# Check 2: Namespace exists
echo -e "${YELLOW}[2/8] Checking namespace...${NC}"
if kubectl get namespace "$NAMESPACE" &>/dev/null; then
    echo -e "${GREEN}✓ Namespace $NAMESPACE exists${NC}"
else
    echo -e "${RED}✗ Namespace $NAMESPACE not found${NC}"
    echo -e "${YELLOW}Run: kubectl create namespace $NAMESPACE${NC}"
    exit 1
fi
echo ""

# Check 3: Pod status
echo -e "${YELLOW}[3/8] Checking pod status...${NC}"
PODS=$(kubectl get pods -n "$NAMESPACE" -l app=gcp-compute-persistent-disk-csi-driver -o json)
TOTAL_PODS=$(echo "$PODS" | jq '.items | length')

if [ "$TOTAL_PODS" -eq 0 ]; then
    echo -e "${RED}✗ No CSI driver pods found${NC}"
    echo -e "${YELLOW}CSI driver may not be installed${NC}"
    exit 1
fi

echo -e "${BLUE}Pod Status:${NC}"
kubectl get pods -n "$NAMESPACE" -l app=gcp-compute-persistent-disk-csi-driver

RUNNING_PODS=$(echo "$PODS" | jq '[.items[] | select(.status.phase == "Running")] | length')
FAILED_PODS=$(echo "$PODS" | jq '[.items[] | select(.status.phase == "Failed")] | length')
PENDING_PODS=$(echo "$PODS" | jq '[.items[] | select(.status.phase == "Pending")] | length')

echo ""
echo -e "  Running: ${GREEN}$RUNNING_PODS${NC}"
echo -e "  Failed: ${RED}$FAILED_PODS${NC}"
echo -e "  Pending: ${YELLOW}$PENDING_PODS${NC}"
echo ""

# Check 4: Container statuses
echo -e "${YELLOW}[4/8] Checking container statuses...${NC}"
CONTROLLER_POD=$(kubectl get pods -n "$NAMESPACE" -l app=gcp-compute-persistent-disk-csi-driver -o json | jq -r '.items[] | select(.metadata.name | contains("controller")) | .metadata.name' | head -n 1)

if [ -z "$CONTROLLER_POD" ]; then
    echo -e "${RED}✗ No controller pod found${NC}"
else
    echo -e "${BLUE}Controller Pod: $CONTROLLER_POD${NC}"
    CONTAINER_STATUSES=$(kubectl get pod "$CONTROLLER_POD" -n "$NAMESPACE" -o json | jq -r '.status.containerStatuses[] | "\(.name): \(.ready) (\(.state | keys[0]))"')
    echo "$CONTAINER_STATUSES" | while read -r line; do
        CONTAINER_NAME=$(echo "$line" | cut -d: -f1)
        STATUS=$(echo "$line" | cut -d: -f2-)

        if echo "$STATUS" | grep -q "true (running)"; then
            echo -e "  ${GREEN}✓ $CONTAINER_NAME: Running${NC}"
        else
            echo -e "  ${RED}✗ $CONTAINER_NAME:$STATUS${NC}"
        fi
    done
fi
echo ""

# Check 5: Recent events
echo -e "${YELLOW}[5/8] Checking recent events...${NC}"
EVENTS=$(kubectl get events -n "$NAMESPACE" --sort-by='.lastTimestamp' --field-selector type=Warning -o json | jq -r '.items[-5:] | .[] | "\(.lastTimestamp) \(.reason): \(.message)"' 2>/dev/null || echo "")

if [ -z "$EVENTS" ]; then
    echo -e "${GREEN}✓ No recent warning events${NC}"
else
    echo -e "${YELLOW}Recent warnings:${NC}"
    echo "$EVENTS" | while read -r event; do
        echo -e "${YELLOW}  - $event${NC}"
    done
fi
echo ""

# Check 6: GCP metadata access
echo -e "${YELLOW}[6/8] Checking GCP metadata server access...${NC}"
if curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/zone &>/dev/null; then
    PROJECT_ID=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/project/project-id)
    NODE_SA=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/email)

    echo -e "${GREEN}✓ Metadata server accessible${NC}"
    echo -e "  Project: ${BLUE}$PROJECT_ID${NC}"
    echo -e "  Service Account: ${BLUE}$NODE_SA${NC}"
else
    echo -e "${RED}✗ Cannot access metadata server${NC}"
    echo -e "${YELLOW}This may indicate a network or GCP configuration issue${NC}"
fi
echo ""

# Check 7: StorageClasses
echo -e "${YELLOW}[7/8] Checking StorageClasses...${NC}"
SC_COUNT=$(kubectl get storageclass -o json | jq '[.items[] | select(.provisioner == "pd.csi.storage.gke.io")] | length')

if [ "$SC_COUNT" -eq 0 ]; then
    echo -e "${RED}✗ No GCP PD StorageClasses found${NC}"
    echo -e "${YELLOW}StorageClasses may not have been created${NC}"
else
    echo -e "${GREEN}✓ Found $SC_COUNT GCP PD StorageClasses${NC}"
    kubectl get storageclass -o json | jq -r '.items[] | select(.provisioner == "pd.csi.storage.gke.io") | .metadata.name' | while read -r sc; do
        echo -e "  - ${BLUE}$sc${NC}"
    done
fi
echo ""

# Check 8: CSI Driver registration
echo -e "${YELLOW}[8/8] Checking CSI Driver registration...${NC}"
if kubectl get csidriver pd.csi.storage.gke.io &>/dev/null; then
    echo -e "${GREEN}✓ CSI Driver registered${NC}"
    kubectl get csidriver pd.csi.storage.gke.io -o custom-columns=NAME:.metadata.name,ATTACHREQUIRED:.spec.attachRequired,PODINFOONMOUNT:.spec.podInfoOnMount
else
    echo -e "${RED}✗ CSI Driver not registered${NC}"
fi
echo ""

# Detailed diagnostics if there are issues
if [ "$RUNNING_PODS" -lt "$TOTAL_PODS" ]; then
    echo -e "${RED}============================================${NC}"
    echo -e "${RED}Issues Detected - Detailed Diagnostics${NC}"
    echo -e "${RED}============================================${NC}"
    echo ""

    # Get logs from failed containers
    echo -e "${YELLOW}Collecting logs from problematic pods...${NC}"
    echo ""

    kubectl get pods -n "$NAMESPACE" -o json | jq -r '.items[] | select(.status.phase != "Running") | .metadata.name' | while read -r pod; do
        echo -e "${BLUE}=== Pod: $pod ===${NC}"
        kubectl describe pod "$pod" -n "$NAMESPACE" | tail -n 20
        echo ""

        # Try to get logs from each container
        CONTAINERS=$(kubectl get pod "$pod" -n "$NAMESPACE" -o json | jq -r '.spec.containers[].name')
        echo "$CONTAINERS" | while read -r container; do
            echo -e "${BLUE}--- Container: $container ---${NC}"
            kubectl logs "$pod" -n "$NAMESPACE" -c "$container" --tail=20 2>&1 | head -n 20 || echo "No logs available"
            echo ""
        done
    done

    # Check for common issues
    echo -e "${YELLOW}Common Issue Checklist:${NC}"
    echo ""

    # Issue 1: IAM Permissions
    echo -e "${BLUE}1. IAM Permissions:${NC}"
    if curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/email &>/dev/null; then
        echo -e "   Run: ${GREEN}./verify-iam-permissions.sh${NC}"
    else
        echo -e "   ${YELLOW}Cannot check (not on GCP)${NC}"
    fi

    # Issue 2: Workload Identity misconfiguration
    echo -e "${BLUE}2. Workload Identity:${NC}"
    WI_ANNOTATION=$(kubectl get sa csi-gce-pd-controller-sa -n "$NAMESPACE" -o jsonpath='{.metadata.annotations.iam\.gke\.io/gcp-service-account}' 2>/dev/null || echo "")
    if [ -n "$WI_ANNOTATION" ]; then
        echo -e "   ${YELLOW}⚠ Workload Identity annotation found: $WI_ANNOTATION${NC}"
        echo -e "   ${YELLOW}This should be removed for K3s clusters${NC}"
    else
        echo -e "   ${GREEN}✓ No Workload Identity annotations (correct for K3s)${NC}"
    fi

    # Issue 3: --enable-multitenancy flag
    echo -e "${BLUE}3. Multitenancy Flag:${NC}"
    MULTITENANCY_FLAG=$(kubectl get deployment csi-gce-pd-controller -n "$NAMESPACE" -o json | jq -r '.spec.template.spec.containers[] | select(.name == "gce-pd-driver") | .args[] | select(contains("enable-multitenancy"))' || echo "")
    if [ -n "$MULTITENANCY_FLAG" ]; then
        echo -e "   ${YELLOW}⚠ --enable-multitenancy flag found${NC}"
        echo -e "   ${YELLOW}This flag should be removed for K3s${NC}"
    else
        echo -e "   ${GREEN}✓ No multitenancy flag (correct for K3s)${NC}"
    fi

    # Issue 4: Volume mounts
    echo -e "${BLUE}4. Cloud SA Volume:${NC}"
    CLOUD_SA_VOLUME=$(kubectl get deployment csi-gce-pd-controller -n "$NAMESPACE" -o json | jq -r '.spec.template.spec.volumes[] | select(.name == "cloud-sa") | .name' 2>/dev/null || echo "")
    if [ -n "$CLOUD_SA_VOLUME" ]; then
        echo -e "   ${YELLOW}⚠ cloud-sa volume found${NC}"
        echo -e "   ${YELLOW}This should be removed for node SA authentication${NC}"
    else
        echo -e "   ${GREEN}✓ No cloud-sa volume (correct for node SA auth)${NC}"
    fi

    echo ""
    echo -e "${YELLOW}Recommended Actions:${NC}"
    echo -e "1. Verify IAM permissions: ${GREEN}./verify-iam-permissions.sh${NC}"
    echo -e "2. Reinstall CSI driver: ${GREEN}./install-gcp-pd-csi.sh${NC}"
    echo -e "3. Check logs: ${GREEN}kubectl logs -n $NAMESPACE -l app=gcp-compute-persistent-disk-csi-driver --all-containers --tail=100${NC}"
    echo ""
fi

# Summary
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Diagnostic Summary${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

if [ "$RUNNING_PODS" -eq "$TOTAL_PODS" ] && [ "$SC_COUNT" -gt 0 ]; then
    echo -e "${GREEN}✓ CSI Driver appears healthy${NC}"
    echo -e "${GREEN}✓ All pods running${NC}"
    echo -e "${GREEN}✓ StorageClasses configured${NC}"
    echo ""
    echo -e "${BLUE}You can test volume provisioning with:${NC}"
    echo -e "${GREEN}kubectl apply -f - <<EOF
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: test-pvc
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 1Gi
  storageClassName: gcp-pd-balanced
EOF${NC}"
else
    echo -e "${YELLOW}⚠ CSI Driver has issues${NC}"
    echo -e "${YELLOW}Review the diagnostic output above${NC}"
    echo ""
    echo -e "${BLUE}For detailed troubleshooting, see:${NC}"
    echo -e "${GREEN}cat ~/.k8s/docs/CSI-DRIVER-TROUBLESHOOTING.md${NC}"
fi

echo ""
exit 0
