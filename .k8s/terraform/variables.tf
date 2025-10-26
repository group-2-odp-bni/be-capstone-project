# Terraform Variables for GCP K3s Infrastructure
# This file defines all configurable parameters for the infrastructure

variable "project_id" {
  description = "GCP Project ID where resources will be created"
  type        = string
}

variable "region" {
  description = "GCP region for resource deployment (e.g., asia-southeast2 for Jakarta)"
  type        = string
  default     = "asia-southeast2"
}

variable "zone" {
  description = "GCP zone for compute instances (e.g., asia-southeast2-a)"
  type        = string
  default     = "asia-southeast2-a"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "project_name" {
  description = "Project name used for resource naming and tagging"
  type        = string
  default     = "orange-wallet"
}

# Network Configuration
variable "vpc_cidr" {
  description = "CIDR block for VPC network"
  type        = string
  default     = "10.0.0.0/16"
}

variable "subnet_cidr" {
  description = "CIDR block for subnet"
  type        = string
  default     = "10.0.1.0/24"
}

# Compute Configuration - Master Node
variable "master_machine_type" {
  description = "GCP machine type for K3s master node (e.g., e2-medium, e2-standard-2)"
  type        = string
  default     = "e2-small" # 2 vCPU, 2 GB RAM - sufficient for K3s master
}

variable "master_disk_size" {
  description = "Boot disk size for master node in GB"
  type        = number
  default     = 30
}

variable "master_disk_type" {
  description = "Boot disk type for master node (pd-standard, pd-ssd, pd-balanced)"
  type        = string
  default     = "pd-standard"
}

# Compute Configuration - Worker Node
variable "worker_count" {
  description = "Number of K3s worker nodes (scale horizontally by increasing this value)"
  type        = number
  default     = 1

  validation {
    condition     = var.worker_count >= 1 && var.worker_count <= 10
    error_message = "Worker count must be between 1 and 10 for cost and management purposes."
  }
}

variable "worker_machine_type" {
  description = "GCP machine type for K3s worker node"
  type        = string
  default     = "e2-medium" # 2 vCPU, 4 GB RAM
}

variable "worker_disk_size" {
  description = "Boot disk size for worker node in GB"
  type        = number
  default     = 50
}

variable "worker_disk_type" {
  description = "Boot disk type for worker node"
  type        = string
  default     = "pd-standard"
}

# K3s Configuration
variable "k3s_version" {
  description = "K3s version to install (empty string for latest)"
  type        = string
  default     = "" # Latest stable version
}

variable "k3s_token" {
  description = "K3s cluster token for node authentication (leave empty for auto-generation)"
  type        = string
  default     = ""
  sensitive   = true
}



# SSH Configuration
variable "ssh_public_key_path" {
  description = "Path to SSH public key for VM access"
  type        = string
  default     = "~/.ssh/id_rsa.pub"
}

variable "ssh_user" {
  description = "SSH username for VM access"
  type        = string
  default     = "ubuntu"
}

# Tagging
variable "labels" {
  description = "Additional labels/tags for all resources"
  type        = map(string)
  default = {
    managed_by = "terraform"
    project    = "orange-wallet"
  }
}

# Enable/Disable Features
variable "enable_preemptible_vms" {
  description = "Use preemptible VMs for cost savings (not recommended for production)"
  type        = bool
  default     = true
}

variable "enable_monitoring" {
  description = "Enable GCP monitoring and logging for VMs"
  type        = bool
  default     = false
}

variable "enable_external_ip" {
  description = "Assign external IP addresses to VMs"
  type        = bool
  default     = true
}
