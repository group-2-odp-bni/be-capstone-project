# Compute Module

## Overview

This module creates the VM instances for the Orange Wallet K3s cluster.

## Resources Created

- **Master Node**: 1x e2-standard-2 (2 vCPU, 8GB RAM)
  - K3s control plane + embedded etcd
  - External IP for API server access
  - 50GB SSD boot disk

- **Stateless Worker Nodes**: 2x e2-medium (2 vCPU, 4GB RAM)
  - Application workloads (Deployments)
  - External IPs for ingress traffic
  - 50GB SSD boot disks

- **Stateful Worker Node**: 1x e2-standard-2 (2 vCPU, 8GB RAM)
  - Database workloads (StatefulSets)
  - Tainted to prevent stateless workloads
  - 50GB SSD boot disk

- **Instance Group**: For load balancer backend

## Usage

```hcl
module "compute" {
  source = "../../modules/compute"

  project_id  = "orange-wallet-project"
  region      = "asia-southeast2"
  zone        = "asia-southeast2-a"
  environment = "dev"

  network_name     = module.network.network_name
  subnet_name      = module.network.subnet_name
  subnet_self_link = module.network.subnet_self_link

  master_machine_type           = "e2-standard-2"
  worker_stateless_machine_type = "e2-medium"
  worker_stateful_machine_type  = "e2-standard-2"

  master_disk_size = 50
  worker_disk_size = 50

  ssh_user            = "ubuntu"
  ssh_public_key_path = "~/.ssh/orange-wallet-key.pub"

  service_account_email = "k3s-node-sa@project.iam.gserviceaccount.com"

  labels = {
    project = "orange-wallet"
  }
}
```

## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|----------|
| project_id | GCP Project ID | string | - | yes |
| region | GCP region | string | - | yes |
| zone | GCP zone | string | - | yes |
| network_name | VPC network name | string | - | yes |
| subnet_self_link | Subnet self-link | string | - | yes |
| master_machine_type | Master node machine type | string | - | yes |
| worker_stateless_machine_type | Stateless worker machine type | string | - | yes |
| worker_stateful_machine_type | Stateful worker machine type | string | - | yes |
| ssh_user | SSH username | string | - | yes |
| ssh_public_key_path | SSH public key path | string | - | yes |
| service_account_email | Service account email | string | - | yes |

## Outputs

| Name | Description |
|------|-------------|
| master_node_name | Master node name |
| master_internal_ip | Master internal IP |
| master_external_ip | Master external IP |
| worker_nodes | Map of worker node details |
| worker_instance_groups | Instance groups for LB |

## Node Configuration

### Master Node
- **Hostname**: orange-wallet-master
- **Role**: K3s server (control plane)
- **Labels**: node-role=master, workload-type=control-plane
- **Tags**: k3s-node, k3s-master

### Worker Nodes
- **Worker-1**: orange-wallet-worker-1 (stateless)
  - Labels: node-role=worker, workload-type=stateless
  - Tags: k3s-node, k3s-worker, stateless

- **Worker-2**: orange-wallet-worker-2 (stateless)
  - Labels: node-role=worker, workload-type=stateless
  - Tags: k3s-node, k3s-worker, stateless

- **Worker-3**: orange-wallet-worker-3 (stateful)
  - Labels: node-role=worker, workload-type=stateful
  - Tags: k3s-node, k3s-worker, stateful
  - **Note**: Will be tainted by Ansible with `stateful=true:NoSchedule`

## Cloud-Init Configuration

Each node is provisioned with cloud-init to:
1. Disable swap (Kubernetes requirement)
2. Load required kernel modules (br_netfilter, overlay)
3. Configure sysctl parameters for networking
4. Install essential packages
5. Set hostname
6. Prepare for K3s installation

## Security

- Service account attached with minimal permissions:
  - logging.logWriter
  - monitoring.metricWriter
  - storage.objectViewer

- SSH access controlled via firewall rules
- External IPs for management (can be removed in production)
- Boot disks encrypted by default (Google-managed keys)

## Future Enhancements

- [ ] Use custom images with pre-installed packages
- [ ] Implement OS patch management
- [ ] Add monitoring agents (node_exporter)
- [ ] Implement automatic backups of boot disks
- [ ] Consider using MIGs (Managed Instance Groups) for workers
