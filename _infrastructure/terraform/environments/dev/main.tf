# =============================================================================
# Orange Wallet Infrastructure - Development Environment
# =============================================================================
#
# This is the main entry point for the development environment infrastructure.
# It orchestrates all modules to create a production-ready K3s cluster.
#
# Architecture:
# - 1 Master Node (e2-standard-2): K3s control plane
# - 2 Stateless Workers (e2-medium): Application workloads
# - 1 Stateful Worker (e2-standard-2): Database workloads
#
# =============================================================================

locals {
  # Common tags for all resources
  common_labels = merge(
    var.labels,
    {
      environment = var.environment
      terraform   = "true"
    }
  )

  # Construct service account email if not provided
  k3s_node_sa_email = var.k3s_node_sa_email != "" ? var.k3s_node_sa_email : "k3s-node-sa@${var.project_id}.iam.gserviceaccount.com"
}

# =============================================================================
# Network Module
# =============================================================================

module "network" {
  source = "../../modules/network"

  project_id  = var.project_id
  region      = var.region
  environment = var.environment

  vpc_name     = var.vpc_name
  subnet_cidr  = var.subnet_cidr
  pod_cidr     = var.pod_cidr
  service_cidr = var.service_cidr

  enable_cloud_nat = var.enable_cloud_nat

  labels = local.common_labels
}

# =============================================================================
# Compute Module - K3s Cluster Nodes
# =============================================================================

module "compute" {
  source = "../../modules/compute"

  project_id  = var.project_id
  region      = var.region
  zone        = var.zone
  environment = var.environment

  # Network configuration
  network_name    = module.network.network_name
  subnet_name     = module.network.subnet_name
  subnet_self_link = module.network.subnet_self_link

  # Master node configuration
  master_machine_type = var.master_machine_type
  master_disk_size    = var.master_disk_size

  # Worker node configuration
  worker_stateless_machine_type = var.worker_stateless_machine_type
  worker_stateful_machine_type  = var.worker_stateful_machine_type
  worker_disk_size              = var.worker_disk_size

  # SSH configuration
  ssh_user            = var.ssh_user
  ssh_public_key_path = var.ssh_public_key_path

  # Service account
  service_account_email = local.k3s_node_sa_email

  labels = local.common_labels

  depends_on = [module.network]
}

# =============================================================================
# Load Balancer Module
# =============================================================================

module "loadbalancer" {
  source = "../../modules/loadbalancer"

  project_id  = var.project_id
  region      = var.region
  environment = var.environment

  network_name = module.network.network_name

  # Pass the worker instance self-links and network tag to the LB module
  worker_instances_self_links = module.compute.worker_instances_self_links
  worker_nodes_tag            = module.compute.worker_nodes_tag

  labels = local.common_labels

  depends_on = [module.compute]
}

# =============================================================================
# Storage Module - GCS Buckets
# =============================================================================

module "storage" {
  source = "../../modules/storage"

  project_id  = var.project_id
  region      = var.region
  environment = var.environment

  backup_bucket_name    = var.backup_bucket_name
  backup_retention_days = var.backup_retention_days

  labels = local.common_labels
}

# =============================================================================
# Auto-generate Ansible Inventory
# =============================================================================

resource "local_file" "ansible_inventory" {
  content  = module.compute.ansible_inventory
  filename = "${path.module}/../../ansible/inventory/hosts.yml"

  file_permission = "0644"

  depends_on = [module.compute]
}

# =============================================================================
# Outputs
# =============================================================================
#
# These outputs will be used by Ansible for cluster configuration
#
# =============================================================================
