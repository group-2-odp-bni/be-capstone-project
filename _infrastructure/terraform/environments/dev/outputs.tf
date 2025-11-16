# =============================================================================
# Terraform Outputs - Development Environment
# =============================================================================
#
# These outputs are used by:
# 1. Ansible inventory generation
# 2. Manual verification
# 3. CI/CD pipelines
#
# =============================================================================

# -----------------------------------------------------------------------------
# Network Outputs
# -----------------------------------------------------------------------------

output "network_name" {
  description = "Name of the VPC network"
  value       = module.network.network_name
}

output "subnet_name" {
  description = "Name of the subnet"
  value       = module.network.subnet_name
}

output "subnet_cidr" {
  description = "CIDR range of the subnet"
  value       = module.network.subnet_cidr
}

# -----------------------------------------------------------------------------
# Compute Outputs - Master Node
# -----------------------------------------------------------------------------

output "master_node_name" {
  description = "Name of the K3s master node"
  value       = module.compute.master_node_name
}

output "master_internal_ip" {
  description = "Internal IP address of the master node"
  value       = module.compute.master_internal_ip
}

output "master_external_ip" {
  description = "External IP address of the master node"
  value       = module.compute.master_external_ip
}

# -----------------------------------------------------------------------------
# Compute Outputs - Worker Nodes
# -----------------------------------------------------------------------------

output "worker_nodes" {
  description = "Map of worker node details"
  value       = module.compute.worker_nodes
}

output "worker_internal_ips" {
  description = "List of internal IPs for worker nodes"
  value       = module.compute.worker_internal_ips
}

output "worker_external_ips" {
  description = "List of external IPs for worker nodes"
  value       = module.compute.worker_external_ips
}

# -----------------------------------------------------------------------------
# Load Balancer Outputs
# -----------------------------------------------------------------------------

output "load_balancer_ip" {
  description = "External IP address of the load balancer"
  value       = module.loadbalancer.external_ip
}

output "load_balancer_url" {
  description = "URL to access services via load balancer"
  value       = "http://${module.loadbalancer.external_ip}"
}

# -----------------------------------------------------------------------------
# Storage Outputs
# -----------------------------------------------------------------------------

output "backup_bucket_name" {
  description = "Name of the backup bucket"
  value       = module.storage.backup_bucket_name
}

output "backup_bucket_url" {
  description = "GCS URL of the backup bucket"
  value       = module.storage.backup_bucket_url
}

# -----------------------------------------------------------------------------
# Ansible Inventory Data
# -----------------------------------------------------------------------------

output "ansible_inventory_data" {
  description = "Data for generating Ansible inventory"
  value = {
    master = {
      name        = module.compute.master_node_name
      internal_ip = module.compute.master_internal_ip
      external_ip = module.compute.master_external_ip
    }
    workers = module.compute.worker_nodes
  }
  sensitive = false
}

# -----------------------------------------------------------------------------
# Connection Information
# -----------------------------------------------------------------------------

output "ssh_connection_commands" {
  description = "SSH connection commands for all nodes"
  value = {
    master  = "ssh ${var.ssh_user}@${module.compute.master_external_ip}"
    workers = [for node in module.compute.worker_nodes : "ssh ${var.ssh_user}@${node.external_ip}"]
  }
}

# -----------------------------------------------------------------------------
# Summary Output
# -----------------------------------------------------------------------------

output "infrastructure_summary" {
  description = "Summary of deployed infrastructure"
  value = <<-EOT

  ========================================
  Orange Wallet Infrastructure - ${upper(var.environment)}
  ========================================

  Region: ${var.region}
  Zone: ${var.zone}

  MASTER NODE:
  - Name: ${module.compute.master_node_name}
  - Internal IP: ${module.compute.master_internal_ip}
  - External IP: ${module.compute.master_external_ip}
  - SSH: ssh ${var.ssh_user}@${module.compute.master_external_ip}

  WORKER NODES:
  ${join("\n  ", [for node in module.compute.worker_nodes : "- ${node.name}: ${node.internal_ip} (${node.external_ip})"])}

  LOAD BALANCER:
  - IP: ${module.loadbalancer.external_ip}
  - URL: http://${module.loadbalancer.external_ip}

  STORAGE:
  - Backup Bucket: ${module.storage.backup_bucket_name}

  NEXT STEPS:
  1. Run Ansible playbook to install K3s
  2. Configure kubectl: export KUBECONFIG=/path/to/kubeconfig
  3. Verify cluster: kubectl get nodes

  ========================================
  EOT
}
