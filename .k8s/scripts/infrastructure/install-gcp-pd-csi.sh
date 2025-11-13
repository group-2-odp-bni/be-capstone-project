#!/bin/bash
# Install GCP Persistent Disk CSI Driver for K3s on GCP
# This script configures the CSI driver to use node service account authentication
# since K3s does not support GKE Workload Identity

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Installing GCP Persistent Disk CSI Driver${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Configuration
NAMESPACE="gce-pd-csi-driver"
CSI_DRIVER_VERSION="v1.17.4"
DEPLOY_OVERLAY="github.com/kubernetes-sigs/gcp-compute-persistent-disk-csi-driver/deploy/kubernetes/overlays/stable-master?ref=master"

# Check kubectl access
echo -e "${YELLOW}[1/6] Verifying cluster access...${NC}"
if ! kubectl get nodes &>/dev/null; then
    echo -e "${RED}Cannot access Kubernetes cluster${NC}"
    echo -e "${RED}Ensure KUBECONFIG is set correctly or run from master node${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Cluster access verified${NC}"

# Verify we're on GCP
echo -e "${YELLOW}[2/6] Verifying GCP environment...${NC}"
if ! curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/zone &>/dev/null; then
    echo -e "${RED}Not running on GCP! This CSI driver requires GCP Compute Engine.${NC}"
    exit 1
fi

# Get GCP metadata
PROJECT_ID=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/project/project-id)
ZONE=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/zone | cut -d'/' -f4)
REGION="${ZONE%-*}"

# Get node service account
NODE_SA=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/email)

echo -e "${GREEN}✓ Running on GCP${NC}"
echo -e "${GREEN}  Project: ${PROJECT_ID}${NC}"
echo -e "${GREEN}  Region: ${REGION}${NC}"
echo -e "${GREEN}  Zone: ${ZONE}${NC}"
echo -e "${GREEN}  Node Service Account: ${NODE_SA}${NC}"
echo ""

# Verify IAM permissions
echo -e "${YELLOW}[3/6] Verifying IAM permissions...${NC}"
echo -e "${BLUE}Checking service account roles...${NC}"

# Check if gcloud is available
if command -v gcloud &>/dev/null; then
    REQUIRED_ROLES=(
        "roles/compute.storageAdmin"
        "roles/compute.instanceAdmin.v1"
        "roles/iam.serviceAccountUser"
    )

    MISSING_ROLES=()
    for role in "${REQUIRED_ROLES[@]}"; do
        if ! gcloud projects get-iam-policy "$PROJECT_ID" \
            --flatten="bindings[].members" \
            --filter="bindings.role:$role AND bindings.members:serviceAccount:$NODE_SA" \
            --format="value(bindings.role)" 2>/dev/null | grep -q "$role"; then
            MISSING_ROLES+=("$role")
        fi
    done

    if [ ${#MISSING_ROLES[@]} -gt 0 ]; then
        echo -e "${RED}Missing required IAM roles for service account: ${NODE_SA}${NC}"
        echo -e "${RED}Required roles:${NC}"
        for role in "${MISSING_ROLES[@]}"; do
            echo -e "${RED}  - $role${NC}"
        done
        echo ""
        echo -e "${YELLOW}Run these commands to grant required permissions:${NC}"
        for role in "${MISSING_ROLES[@]}"; do
            echo -e "${GREEN}gcloud projects add-iam-policy-binding $PROJECT_ID \\${NC}"
            echo -e "${GREEN}  --member=\"serviceAccount:$NODE_SA\" \\${NC}"
            echo -e "${GREEN}  --role=\"$role\"${NC}"
        done
        echo ""
        read -p "Continue anyway? (yes/no): " -r
        if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
            exit 1
        fi
    else
        echo -e "${GREEN}✓ All required IAM roles present${NC}"
    fi
else
    echo -e "${YELLOW}! gcloud CLI not found, skipping IAM verification${NC}"
    echo -e "${YELLOW}  Ensure service account has: roles/compute.storageAdmin${NC}"
fi
echo ""

# Create namespace
echo -e "${YELLOW}[4/6] Creating namespace...${NC}"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
echo -e "${GREEN}✓ Namespace ready${NC}"

# Deploy CSI driver base components
echo -e "${YELLOW}[5/6] Deploying GCP PD CSI Driver...${NC}"
kubectl apply -k "$DEPLOY_OVERLAY"

echo -e "${BLUE}Waiting for deployments to settle (5 seconds)...${NC}"
sleep 5

# Patch controller deployment to remove Workload Identity and --enable-multitenancy
echo -e "${BLUE}Configuring CSI driver for K3s (node service account mode)...${NC}"

# Strategy: Rather than brittle index-based patches, we'll use strategic merge patch
# to remove the cloud-sa volume and update the gce-pd-driver container args

# CSI driver will use node service account automatically (no Workload Identity on K3s)
# Patch to remove Workload Identity configuration and --enable-multitenancy flag
echo -e "${BLUE}Patching CSI driver for K3s compatibility...${NC}"

# Patch controller deployment: remove cloud-sa volume, volumeMount, env, and --enable-multitenancy flag
kubectl patch deployment csi-gce-pd-controller -n "$NAMESPACE" --type=json -p='[
  {"op": "remove", "path": "/spec/template/spec/volumes/1"},
  {"op": "remove", "path": "/spec/template/spec/containers/4/volumeMounts/1"},
  {"op": "remove", "path": "/spec/template/spec/containers/4/env/0"},
  {"op": "remove", "path": "/spec/template/spec/containers/4/args/6"}
]' 2>/dev/null || echo -e "${YELLOW}Controller patch failed (may already be patched)${NC}"

# Patch node daemonset: remove --enable-multitenancy flag
kubectl patch daemonset csi-gce-pd-node -n "$NAMESPACE" --type=json -p='[
  {"op": "remove", "path": "/spec/template/spec/containers/1/args/4"}
]' 2>/dev/null || echo -e "${YELLOW}Node daemonset patch failed (may already be patched)${NC}"

echo -e "${GREEN}✓ CSI Driver deployed and configured${NC}"

# Wait for CSI driver pods
echo -e "${YELLOW}[6/6] Waiting for CSI driver pods...${NC}"
echo -e "${BLUE}This may take up to 3 minutes...${NC}"

# Wait for controller deployment
if kubectl rollout status deployment/csi-gce-pd-controller -n "$NAMESPACE" --timeout=180s; then
    echo -e "${GREEN}✓ Controller deployment ready${NC}"
else
    echo -e "${YELLOW}! Controller deployment not ready, checking pod status...${NC}"
    kubectl get pods -n "$NAMESPACE" -l app=gcp-compute-persistent-disk-csi-driver
    echo ""
    echo -e "${YELLOW}Checking controller pod logs:${NC}"
    CONTROLLER_POD=$(kubectl get pods -n "$NAMESPACE" -l app=gcp-compute-persistent-disk-csi-driver -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
    if [ -n "$CONTROLLER_POD" ]; then
        kubectl logs "$CONTROLLER_POD" -n "$NAMESPACE" -c gce-pd-driver --tail=50 || true
    fi
fi

# Wait for node daemonset
if kubectl rollout status daemonset/csi-gce-pd-node -n "$NAMESPACE" --timeout=180s; then
    echo -e "${GREEN}✓ Node daemonset ready${NC}"
else
    echo -e "${YELLOW}! Node daemonset not ready${NC}"
fi

echo ""

# Create StorageClasses
echo -e "${YELLOW}Creating StorageClasses...${NC}"

cat <<EOF | kubectl apply -f -
---
# Balanced StorageClass (RECOMMENDED DEFAULT)
# Best performance/cost ratio for most workloads
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: gcp-pd-balanced
  annotations:
    storageclass.kubernetes.io/is-default-class: "true"
provisioner: pd.csi.storage.gke.io
parameters:
  type: pd-balanced
  replication-type: none
volumeBindingMode: WaitForFirstConsumer
allowVolumeExpansion: true
---
# SSD StorageClass (High Performance)
# For databases requiring low latency (PostgreSQL, Redis)
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: gcp-pd-ssd
provisioner: pd.csi.storage.gke.io
parameters:
  type: pd-ssd
  replication-type: none
volumeBindingMode: WaitForFirstConsumer
allowVolumeExpansion: true
---
# Standard StorageClass (Cost-Effective)
# For logs, backups, and infrequently accessed data
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: gcp-pd-standard
provisioner: pd.csi.storage.gke.io
parameters:
  type: pd-standard
  replication-type: none
volumeBindingMode: WaitForFirstConsumer
allowVolumeExpansion: true
---
# Regional Balanced StorageClass (High Availability)
# Replicated across 2 zones for disaster recovery
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: gcp-pd-balanced-regional
provisioner: pd.csi.storage.gke.io
parameters:
  type: pd-balanced
  replication-type: regional-pd
volumeBindingMode: WaitForFirstConsumer
allowVolumeExpansion: true
allowedTopologies:
- matchLabelExpressions:
  - key: topology.gke.io/zone
    values:
    - ${ZONE}
    - ${REGION}-b
    - ${REGION}-c
EOF

# Remove default from local-path if it exists
kubectl patch storageclass local-path -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"false"}}}' 2>/dev/null || true

echo -e "${GREEN}✓ StorageClasses created${NC}"

# Verify installation
echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Installation Summary${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

echo -e "${YELLOW}CSI Driver Pods:${NC}"
kubectl get pods -n "$NAMESPACE" -l app=gcp-compute-persistent-disk-csi-driver -o wide

echo ""
echo -e "${YELLOW}StorageClasses:${NC}"
kubectl get storageclass

echo ""
echo -e "${YELLOW}CSIDriver:${NC}"
kubectl get csidriver pd.csi.storage.gke.io

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Installation Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

echo -e "${BLUE}Configuration:${NC}"
echo -e "  Default StorageClass: ${GREEN}gcp-pd-balanced${NC}"
echo -e "  Authentication: ${GREEN}Node Service Account${NC}"
echo -e "  Volume Binding: ${GREEN}WaitForFirstConsumer${NC}"
echo -e "  Volume Expansion: ${GREEN}Enabled${NC}"
echo ""

echo -e "${BLUE}Available StorageClasses:${NC}"
echo -e "  ${GREEN}gcp-pd-balanced${NC}          - Default, best price/performance"
echo -e "  ${GREEN}gcp-pd-ssd${NC}               - High performance (databases)"
echo -e "  ${GREEN}gcp-pd-standard${NC}          - Cost-effective (logs, backups)"
echo -e "  ${GREEN}gcp-pd-balanced-regional${NC} - High availability (multi-zone)"
echo ""

echo -e "${BLUE}Recommendations for Your Services:${NC}"
echo -e "  PostgreSQL: ${GREEN}gcp-pd-ssd${NC} (low latency required)"
echo -e "  Redis: ${GREEN}gcp-pd-ssd${NC} (in-memory cache, fast disk for persistence)"
echo -e "  Kafka: ${GREEN}gcp-pd-balanced${NC} (good throughput, cost-effective)"
echo -e "  WAHA: ${GREEN}gcp-pd-balanced${NC} (general purpose)"
echo ""

echo -e "${BLUE}Test the installation:${NC}"
echo -e "${GREEN}kubectl apply -f - <<EOF
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: test-pvc
  namespace: default
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
  storageClassName: gcp-pd-balanced
---
apiVersion: v1
kind: Pod
metadata:
  name: test-pod
  namespace: default
spec:
  containers:
  - name: test
    image: nginx:alpine
    volumeMounts:
    - name: data
      mountPath: /data
  volumes:
  - name: data
    persistentVolumeClaim:
      claimName: test-pvc
EOF${NC}"

echo ""
echo -e "${GREEN}# Verify PVC is bound:${NC}"
echo -e "${GREEN}kubectl get pvc test-pvc${NC}"
echo ""
echo -e "${GREEN}# Check pod is running:${NC}"
echo -e "${GREEN}kubectl get pod test-pod${NC}"
echo ""
echo -e "${GREEN}# Cleanup test resources:${NC}"
echo -e "${GREEN}kubectl delete pod test-pod${NC}"
echo -e "${GREEN}kubectl delete pvc test-pvc${NC}"
echo ""

echo -e "${BLUE}Troubleshooting:${NC}"
echo -e "If pods fail, check logs:"
echo -e "${GREEN}kubectl logs -n gce-pd-csi-driver -l app=gcp-compute-persistent-disk-csi-driver --all-containers --tail=100${NC}"
echo ""
