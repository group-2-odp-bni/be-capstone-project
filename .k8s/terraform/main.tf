# Main Terraform Configuration for Orange Wallet K3s on GCP
# This orchestrates all modules to provision the complete infrastructure

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.4"
    }
  }

  # Backend configuration for remote state
  # Uncomment and configure for production use
  # backend "gcs" {
  #   bucket = "your-terraform-state-bucket"
  #   prefix = "k3s/state"
  # }
}

provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

# Generate random K3s token if not provided
resource "random_password" "k3s_token" {
  count   = var.k3s_token == "" ? 1 : 0
  length  = 32
  special = false
}

locals {
  k3s_token = var.k3s_token != "" ? var.k3s_token : random_password.k3s_token[0].result

  common_labels = merge(
    var.labels,
    {
      environment = var.environment
      terraform   = "true"
    }
  )
}

# Network Module - VPC, Subnets, Firewall Rules
module "network" {
  source = "./modules/network"

  project_id   = var.project_id
  project_name = var.project_name
  environment  = var.environment
  region       = var.region

  vpc_cidr    = var.vpc_cidr
  subnet_cidr = var.subnet_cidr

  metallb_ip_range_start = var.metallb_ip_range_start
  metallb_ip_range_end   = var.metallb_ip_range_end

  labels = local.common_labels
}

# IAM Module - Service Accounts and Permissions
module "iam" {
  source = "./modules/iam"

  project_id   = var.project_id
  project_name = var.project_name
  environment  = var.environment
}

# Compute Module - GCP VM Instances
module "compute" {
  source = "./modules/compute"

  project_id   = var.project_id
  project_name = var.project_name
  environment  = var.environment
  region       = var.region
  zone         = var.zone

  # Network configuration from network module
  vpc_network_id         = module.network.vpc_network_id
  subnet_id              = module.network.subnet_id
  vpc_network_name       = module.network.vpc_network_name
  subnet_cidr            = var.subnet_cidr
  metallb_ip_range_start = var.metallb_ip_range_start
  metallb_ip_range_end   = var.metallb_ip_range_end

  # IAM configuration from IAM module
  service_account_email = module.iam.service_account_email

  # Master node configuration
  master_machine_type = var.master_machine_type
  master_disk_size    = var.master_disk_size
  master_disk_type    = var.master_disk_type

  # Worker node configuration
  worker_machine_type = var.worker_machine_type
  worker_disk_size    = var.worker_disk_size
  worker_disk_type    = var.worker_disk_type

  # K3s configuration
  k3s_version = var.k3s_version
  k3s_token   = local.k3s_token

  # SSH configuration
  ssh_public_key_path = var.ssh_public_key_path
  ssh_user            = var.ssh_user

  # Feature flags
  enable_preemptible_vms = var.enable_preemptible_vms
  enable_monitoring      = var.enable_monitoring
  enable_external_ip     = var.enable_external_ip

  labels = local.common_labels

  depends_on = [
    module.network,
    module.iam
  ]
}

# Save K3s token to local file for reference (excluded from git)
resource "local_file" "k3s_token" {
  content  = local.k3s_token
  filename = "${path.module}/.k3s-token"
  file_permission = "0600"
}
