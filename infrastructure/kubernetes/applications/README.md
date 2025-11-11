# Kubernetes Applications - Reference/Backup

⚠️ **IMPORTANT: This directory is for REFERENCE/BACKUP only**

## For Showcase Demo

**Active GitOps Deployment:**
- **Repository:** https://github.com/group-2-odp-bni/orange-wallet-gitops
- **Location (local):** `orange-wallet-gitops/` (at project root)
- **Deployment Method:** ArgoCD (GitOps pattern)

**This folder (`infrastructure/kubernetes/applications/`):**
- ✅ **Backup/Reference** - Source of truth sebelum GitOps migration
- ✅ **Documentation** - Architecture reference
- ❌ **NOT USED** for actual deployment in showcase

---

## Why Separate?

### GitOps Repository (`orange-wallet-gitops/`)
- **Purpose:** Production deployment via ArgoCD
- **Structure:**
  - `base/` - Base Kustomize manifests
  - `production/` - Production overlays (image tags updated by CI/CD)
  - `argocd/` - ArgoCD Application definitions
- **Workflow:** Code push → CI/CD → Update GitOps repo → ArgoCD auto-sync
- **Benefits:** Git as source of truth, audit trail, easy rollback

### This Folder (`infrastructure/kubernetes/applications/`)
- **Purpose:** Part of IMPLEMENTATION-BACKLOG Phase 4
- **Structure:**
  - `base/` - Base manifests
  - `overlays/` - Environment overlays
- **Usage:** Traditional kubectl/Helm deployment (not used for showcase)
- **Status:** Keep as backup/reference

---

## Deployment Architecture (Showcase)

```
Developer Push
    ↓
GitHub Actions (CI/CD)
    ↓
Build & Test
    ↓
Docker Push (Docker Hub)
    ↓
Update GitOps Repo (orange-wallet-gitops)
    ↓
ArgoCD Detects Change
    ↓
ArgoCD Auto-Sync
    ↓
K3s Cluster (Deployed)
```

---

## How to Sync Changes

If you need to update manifests:

1. **Edit in GitOps repo:**
   ```bash
   cd orange-wallet-gitops/base/SERVICE-NAME/
   # Edit deployment.yaml, service.yaml, etc.
   git commit -m "Update SERVICE-NAME configuration"
   git push origin main
   ```

2. **ArgoCD will auto-sync** (within 3 minutes)

3. **Optionally update this folder** (for backup):
   ```bash
   # Copy from GitOps repo to here
   cp orange-wallet-gitops/base/SERVICE-NAME/* \
      infrastructure/kubernetes/applications/base/SERVICE-NAME/
   ```

---

## For Future Traditional Deployment (Post-Showcase)

If you want to switch back to traditional deployment:

```bash
# Deploy using kubectl
kubectl apply -k infrastructure/kubernetes/applications/base/

# Or using Helm
helm upgrade --install SERVICE-NAME ./charts/SERVICE-NAME
```

But for showcase, **use GitOps only** (orange-wallet-gitops).

---

**Last Updated:** 2025-01-11
**Status:** Reference/Backup - Not active in showcase deployment
