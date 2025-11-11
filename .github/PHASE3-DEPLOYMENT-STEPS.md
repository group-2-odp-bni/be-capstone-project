# Phase 3: ArgoCD Configuration - Deployment Steps

**Status:** Ready for Execution (after infrastructure deployed)
**Prerequisites:** K3s cluster ready + ArgoCD installed via Ansible (IMPLEMENTATION-BACKLOG Phase 3, task 3.24)

---

## Pre-Deployment Checklist

- [ ] Infrastructure deployed (Terraform executed)
- [ ] K3s cluster running (Ansible executed)
- [ ] ArgoCD installed and accessible
- [ ] kubectl configured with cluster access
- [ ] GitHub secrets configured:
  - `GITOPS_REPO` = `group-2-odp-bni/orange-wallet-gitops`
  - `GITOPS_TOKEN` = (GitHub PAT with repo access)

---

## Deployment Steps

### 1. Verify ArgoCD Installation

```bash
# Check ArgoCD pods
kubectl get pods -n argocd

# Get ArgoCD admin password
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d && echo

# Port-forward ArgoCD UI (if no ingress)
kubectl port-forward svc/argocd-server -n argocd 8080:443

# Access: https://localhost:8080
# Username: admin
# Password: (from above command)
```

### 2. Connect GitOps Repository to ArgoCD

**Via ArgoCD UI:**
1. Settings → Repositories → Connect Repo
2. Method: HTTPS
3. Repository URL: `https://github.com/group-2-odp-bni/orange-wallet-gitops.git`
4. Username: (GitHub username)
5. Password: (GitHub PAT)
6. Click "Connect"

**Via CLI:**
```bash
argocd login localhost:8080 --username admin --insecure

argocd repo add https://github.com/group-2-odp-bni/orange-wallet-gitops.git \
  --username YOUR-GITHUB-USERNAME \
  --password YOUR-GITHUB-PAT
```

### 3. Deploy Infrastructure (Namespace, Ingress, Network Policies)

```bash
# Apply infrastructure manifests manually (one-time setup)
kubectl apply -k orange-wallet-gitops/infrastructure/
```

**Verify:**
```bash
kubectl get namespace orange-wallet
kubectl get ingress -n orange-wallet
kubectl get networkpolicies -n orange-wallet
```

### 4. Deploy App-of-Apps

```bash
# Deploy the root ArgoCD application
kubectl apply -f orange-wallet-gitops/argocd/app-of-apps.yaml
```

**This will automatically create:**
- authentication-service
- user-service
- transaction-service
- wallet-service
- api-gateway

### 5. Verify ArgoCD Applications

**Via UI:**
- Open ArgoCD UI
- Should see 6 applications (1 parent + 5 services)
- All should show "Synced" and "Healthy"

**Via CLI:**
```bash
# List all applications
argocd app list

# Check specific app
argocd app get authentication-service

# Watch sync status
watch argocd app list
```

### 6. Verify Kubernetes Deployments

```bash
# Check all pods in orange-wallet namespace
kubectl get pods -n orange-wallet

# Check deployments
kubectl get deployments -n orange-wallet

# Check services
kubectl get svc -n orange-wallet

# Check HPA
kubectl get hpa -n orange-wallet
```

---

## Validation Checklist

- [ ] ArgoCD UI accessible
- [ ] GitOps repository connected (green status)
- [ ] 6 ArgoCD applications created
- [ ] All applications show "Synced"
- [ ] All applications show "Healthy"
- [ ] All pods running in orange-wallet namespace
- [ ] All services accessible
- [ ] Ingress configured correctly

---

## Troubleshooting

### ArgoCD not syncing
```bash
# Force refresh
argocd app get APP-NAME --hard-refresh

# Force sync
argocd app sync APP-NAME --force
```

### Image pull errors
```bash
# Check image tag in production overlay
cat orange-wallet-gitops/production/SERVICE-NAME/kustomization.yaml

# Verify Docker Hub image exists
docker pull teukumunawar/orange-pay-SERVICE-NAME:TAG
```

### Pod not starting
```bash
# Check pod logs
kubectl logs -n orange-wallet -l app=SERVICE-NAME --tail=50

# Describe pod for events
kubectl describe pod -n orange-wallet POD-NAME
```

---

## Next Steps After Deployment

1. **Test CI/CD Flow:**
   - Make code change in be-capstone-project
   - Push to main branch
   - Verify GitHub Actions runs
   - Verify GitOps repo updated
   - Verify ArgoCD syncs automatically

2. **Setup Monitoring (Optional):**
   - Deploy LGTM stack (IMPLEMENTATION-BACKLOG Phase 6)
   - Configure dashboards
   - Setup alerts

3. **Demo Preparation:**
   - Practice showing ArgoCD UI
   - Practice showing GitOps workflow
   - Practice rollback scenario
   - Prepare architecture diagram

---

## Showcase Demo Flow (Recommended)

1. **Show Architecture:**
   - Explain GitOps pattern
   - Show separation: app repo vs gitops repo

2. **Show CI/CD Pipeline:**
   - Make small code change
   - Show GitHub Actions workflow
   - Show how it updates GitOps repo

3. **Show ArgoCD:**
   - Open ArgoCD UI
   - Show auto-sync in action
   - Show application health status

4. **Show Rollback:**
   - Git revert in GitOps repo
   - Show ArgoCD auto-sync rollback
   - Service rolls back automatically

5. **Highlight Benefits:**
   - Git as single source of truth
   - Full audit trail
   - Easy rollback
   - Declarative desired state
   - Zero-touch deployment

---

**Time Required:** ~30-45 minutes for full deployment
**Estimated First-Time:** 1-2 hours (including troubleshooting)

---

**Last Updated:** 2025-01-11
**Status:** Configuration Ready, Awaiting Infrastructure Deployment
