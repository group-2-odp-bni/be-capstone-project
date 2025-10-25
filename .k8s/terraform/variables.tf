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
  default     = "e2-medium" # 2 vCPU, 4 GB RAM - suitable for K3s master
}

variable "master_disk_size" {
  description = "Boot disk size for master node in GB"
  type        = number
  default     = 50
}

variable "master_disk_type" {
  description = "Boot disk type for master node (pd-standard, pd-ssd, pd-balanced)"
  type        = string
  default     = "pd-balanced"
}

# Compute Configuration - Worker Node
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
  default     = "pd-balanced"
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

# MetalLB IP Range
variable "metallb_ip_range_start" {
  description = "Start of MetalLB IP address pool (last octet, e.g., 200)"
  type        = number
  default     = 200

  validation {
    condition     = var.metallb_ip_range_start >= 2 && var.metallb_ip_range_start <= 250
    error_message = "MetalLB IP range start must be between 2 and 250."
  }
}

variable "metallb_ip_range_end" {
  description = "End of MetalLB IP address pool (last octet, e.g., 250)"
  type        = number
  default     = 250

  validation {
    condition     = var.metallb_ip_range_end >= 2 && var.metallb_ip_range_end <= 254
    error_message = "MetalLB IP range end must be between 2 and 254."
  }
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
  default     = {
    managed_by = "terraform"
    project    = "orange-wallet"
  }
}

# Enable/Disable Features
variable "enable_preemptible_vms" {
  description = "Use preemptible VMs for cost savings (not recommended for production)"
  type        = bool
  default     = false
}

variable "enable_monitoring" {
  description = "Enable GCP monitoring and logging for VMs"
  type        = bool
  default     = true
}

variable "enable_external_ip" {
  description = "Assign external IP addresses to VMs"
  type        = bool
  default     = true
}
