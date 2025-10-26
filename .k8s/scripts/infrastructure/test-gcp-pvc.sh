#!/bin/bash
# Test GCP Persistent Disk CSI Driver PVC provisioning

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Testing GCP PD CSI Driver PVC Provisioning${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Check if StorageClasses exist
echo -e "${YELLOW}[1/5] Checking StorageClasses...${NC}"
if ! kubectl get storageclass gcp-pd-ssd &>/dev/null; then
    echo -e "${RED}StorageClass 'gcp-pd-ssd' not found!${NC}"
    echo -e "${YELLOW}Run: ./install-gcp-pd-csi.sh first${NC}"
    exit 1
fi
echo -e "${GREEN}✓ StorageClasses found${NC}"

# Create test namespace
echo -e "${YELLOW}[2/5] Creating test namespace...${NC}"
kubectl create namespace gcp-storage-test --dry-run=client -o yaml | kubectl apply -f -

# Create test PVC
echo -e "${YELLOW}[3/5] Creating test PVC (1Gi SSD)...${NC}"
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: test-pvc-ssd
  namespace: gcp-storage-test
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
  storageClassName: gcp-pd-ssd
EOF

# Create test pod using the PVC
echo -e "${YELLOW}[4/5] Creating test pod...${NC}"
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: test-pod
  namespace: gcp-storage-test
spec:
  containers:
  - name: test
    image: busybox
    command: ["/bin/sh"]
    args: ["-c", "echo 'Testing GCP PD...' > /data/test.txt && cat /data/test.txt && sleep 3600"]
    volumeMounts:
    - name: storage
      mountPath: /data
  volumes:
  - name: storage
    persistentVolumeClaim:
      claimName: test-pvc-ssd
EOF

# Wait for PVC to bind
echo -e "${YELLOW}Waiting for PVC to bind...${NC}"
kubectl wait --for=jsonpath='{.status.phase}'=Bound pvc/test-pvc-ssd -n gcp-storage-test --timeout=120s

# Wait for pod to be ready
echo -e "${YELLOW}Waiting for pod to be ready...${NC}"
kubectl wait --for=condition=ready pod/test-pod -n gcp-storage-test --timeout=120s

# Verify
echo -e "${YELLOW}[5/5] Verifying...${NC}"
echo ""
echo -e "${YELLOW}PVC Status:${NC}"
kubectl get pvc -n gcp-storage-test

echo ""
echo -e "${YELLOW}PV Status:${NC}"
kubectl get pv | grep gcp-storage-test

echo ""
echo -e "${YELLOW}Pod Status:${NC}"
kubectl get pod -n gcp-storage-test

echo ""
echo -e "${YELLOW}Pod Logs:${NC}"
kubectl logs test-pod -n gcp-storage-test

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}✓ GCP PD CSI Driver is working correctly!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

echo -e "${YELLOW}Cleanup test resources:${NC}"
echo -e "${GREEN}kubectl delete namespace gcp-storage-test${NC}"
echo ""
