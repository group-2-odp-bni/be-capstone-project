#!/bin/bash
# Ceph CSI Storage Provisioner Installation Script
# Installs Ceph CSI driver for persistent storage in K3s

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Ceph CSI Installation${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Note: For a capstone/dev project, we'll use Rook-Ceph which is easier to set up
# than a full external Ceph cluster. Rook provides Ceph storage orchestration.

echo -e "${BLUE}This will install Rook-Ceph operator and create a Ceph cluster${NC}"
echo -e "${BLUE}Rook-Ceph provides cloud-native storage for Kubernetes${NC}"
echo ""

# Check if running as root or with kubectl access
if ! kubectl get nodes &>/dev/null; then
    echo -e "${RED}Cannot access Kubernetes cluster${NC}"
    echo -e "${YELLOW}Please ensure:${NC}"
    echo -e "  1. K3s is installed and running"
    echo -e "  2. KUBECONFIG is set or run with sudo"
    exit 1
fi

echo -e "${YELLOW}Cluster nodes:${NC}"
kubectl get nodes
echo ""

# Install Rook Operator
echo -e "${YELLOW}[1/5] Installing Rook-Ceph Operator...${NC}"

# Add Rook Helm repository
helm repo add rook-release https://charts.rook.io/release 2>/dev/null || true
helm repo update

# Install Rook Operator
helm upgrade --install rook-ceph rook-release/rook-ceph \
    --namespace rook-ceph \
    --create-namespace \
    --set csi.enableRbdDriver=true \
    --set csi.enableCephfsDriver=true \
    --set resources.limits.cpu=500m \
    --set resources.limits.memory=512Mi \
    --set resources.requests.cpu=100m \
    --set resources.requests.memory=128Mi \
    --wait \
    --timeout 5m

echo -e "${GREEN}Rook operator installed${NC}"

# Wait for Rook operator to be ready
echo -e "${YELLOW}[2/5] Waiting for Rook operator to be ready...${NC}"
kubectl wait --for=condition=ready pod \
    -l app=rook-ceph-operator \
    -n rook-ceph \
    --timeout=300s

# Create Ceph Cluster
echo -e "${YELLOW}[3/5] Creating Ceph Cluster...${NC}"

cat <<EOF | kubectl apply -f -
apiVersion: ceph.rook.io/v1
kind: CephCluster
metadata:
  name: rook-ceph
  namespace: rook-ceph
spec:
  # Ceph version
  cephVersion:
    image: quay.io/ceph/ceph:v18.2.1
    allowUnsupported: false

  # Data directory on host
  dataDirHostPath: /var/lib/rook

  # Skip upgrade checks for dev/testing
  skipUpgradeChecks: false
  continueUpgradeAfterChecksEvenIfNotHealthy: false

  # Wait for healthy cluster before upgrade
  waitTimeoutForHealthyOSDInMinutes: 10

  mon:
    # Number of monitors (1 for non-HA, 3 for HA)
    count: 1
    allowMultiplePerNode: true
    volumeClaimTemplate:
      spec:
        storageClassName: local-path
        resources:
          requests:
            storage: 10Gi

  mgr:
    count: 1
    allowMultiplePerNode: true
    modules:
      - name: pg_autoscaler
        enabled: true

  dashboard:
    enabled: true
    ssl: false

  crashCollector:
    disable: false

  storage:
    useAllNodes: true
    useAllDevices: false
    # Use local-path storage for OSDs
    storageClassDeviceSets:
      - name: set1
        count: 1
        portable: false
        encrypted: false
        volumeClaimTemplates:
          - metadata:
              name: data
            spec:
              resources:
                requests:
                  storage: 20Gi
              storageClassName: local-path
              volumeMode: Block
              accessModes:
                - ReadWriteOnce

  # Placement for dev/small clusters
  placement:
    all:
      tolerations:
        - effect: NoSchedule
          key: node-role.kubernetes.io/master
          operator: Exists

  # Resource limits for dev environment
  resources:
    mgr:
      limits:
        cpu: "500m"
        memory: "512Mi"
      requests:
        cpu: "100m"
        memory: "128Mi"
    mon:
      limits:
        cpu: "500m"
        memory: "512Mi"
      requests:
        cpu: "100m"
        memory: "128Mi"
    osd:
      limits:
        cpu: "1000m"
        memory: "2Gi"
      requests:
        cpu: "200m"
        memory: "512Mi"

  # Health checks
  healthCheck:
    daemonHealth:
      mon:
        interval: 45s
      osd:
        interval: 60s
      status:
        interval: 60s
    livenessProbe:
      mon:
        disabled: false
      mgr:
        disabled: false
      osd:
        disabled: false
EOF

echo -e "${GREEN}Ceph cluster manifest applied${NC}"
echo -e "${BLUE}Waiting for Ceph cluster to be ready (this may take 3-5 minutes)...${NC}"

# Wait for Ceph cluster (with timeout)
TIMEOUT=600
ELAPSED=0
while [ $ELAPSED -lt $TIMEOUT ]; do
    if kubectl get cephcluster -n rook-ceph rook-ceph -o jsonpath='{.status.phase}' 2>/dev/null | grep -q "Ready"; then
        echo -e "${GREEN}Ceph cluster is ready!${NC}"
        break
    fi
    echo -e "${YELLOW}Waiting for Ceph cluster... ($ELAPSED/$TIMEOUT seconds)${NC}"
    sleep 15
    ELAPSED=$((ELAPSED + 15))
done

if [ $ELAPSED -ge $TIMEOUT ]; then
    echo -e "${RED}Timeout waiting for Ceph cluster${NC}"
    echo -e "${YELLOW}Checking cluster status...${NC}"
    kubectl get cephcluster -n rook-ceph -o wide
    echo ""
    echo -e "${YELLOW}Ceph pods:${NC}"
    kubectl get pods -n rook-ceph
    exit 1
fi

# Create Storage Class
echo -e "${YELLOW}[4/5] Creating Ceph RBD Storage Class...${NC}"

cat <<EOF | kubectl apply -f -
apiVersion: ceph.rook.io/v1
kind: CephBlockPool
metadata:
  name: replicapool
  namespace: rook-ceph
spec:
  failureDomain: osd
  replicated:
    size: 2  # Number of replicas (2 for 2-node cluster)
    requireSafeReplicaSize: false  # Allow degraded state for dev
---
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: ceph-rbd
  annotations:
    storageclass.kubernetes.io/is-default-class: "true"
provisioner: rook-ceph.rbd.csi.ceph.com
parameters:
  clusterID: rook-ceph
  pool: replicapool
  imageFormat: "2"
  imageFeatures: layering
  csi.storage.k8s.io/provisioner-secret-name: rook-csi-rbd-provisioner
  csi.storage.k8s.io/provisioner-secret-namespace: rook-ceph
  csi.storage.k8s.io/controller-expand-secret-name: rook-csi-rbd-provisioner
  csi.storage.k8s.io/controller-expand-secret-namespace: rook-ceph
  csi.storage.k8s.io/node-stage-secret-name: rook-csi-rbd-node
  csi.storage.k8s.io/node-stage-secret-namespace: rook-ceph
  csi.storage.k8s.io/fstype: ext4
allowVolumeExpansion: true
reclaimPolicy: Delete
EOF

echo -e "${GREEN}Storage class created${NC}"

# Remove default from local-path storage class
echo -e "${YELLOW}[5/5] Updating storage classes...${NC}"
kubectl patch storageclass local-path \
    -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"false"}}}' \
    2>/dev/null || echo -e "${YELLOW}Could not update local-path storage class${NC}"

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Ceph CSI Installation Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Display status
echo -e "${YELLOW}Storage Classes:${NC}"
kubectl get storageclass
echo ""

echo -e "${YELLOW}Ceph Cluster Status:${NC}"
kubectl get cephcluster -n rook-ceph
echo ""

echo -e "${YELLOW}Ceph Pods:${NC}"
kubectl get pods -n rook-ceph
echo ""

echo -e "${YELLOW}Ceph Health:${NC}"
kubectl -n rook-ceph exec -it deploy/rook-ceph-tools -- ceph status 2>/dev/null || \
    echo -e "${YELLOW}Ceph tools not ready yet. Run this later to check health:${NC}"
    echo -e "${GREEN}kubectl -n rook-ceph exec -it deploy/rook-ceph-tools -- ceph status${NC}"

echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Ceph Dashboard (optional):${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "To access Ceph dashboard:"
echo -e "1. Get password: ${GREEN}kubectl -n rook-ceph get secret rook-ceph-dashboard-password -o jsonpath=\"{['data']['password']}\" | base64 --decode${NC}"
echo -e "2. Port forward: ${GREEN}kubectl -n rook-ceph port-forward svc/rook-ceph-mgr-dashboard 7000${NC}"
echo -e "3. Access at: ${GREEN}http://localhost:7000${NC}"
echo -e "   Username: ${GREEN}admin${NC}"
echo ""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Testing Storage:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "To test the storage, create a PVC:"
echo -e "${GREEN}kubectl apply -f - <<EOF
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: test-pvc
spec:
  storageClassName: ceph-rbd
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
EOF${NC}"
echo ""
echo -e "Then check: ${GREEN}kubectl get pvc test-pvc${NC}"
echo ""
