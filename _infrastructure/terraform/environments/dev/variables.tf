# =============================================================================
# Terraform Variables - Development Environment
# =============================================================================

# -----------------------------------------------------------------------------
# Project Configuration
# -----------------------------------------------------------------------------

variable "project_id" {
  description = "GCP Project ID"
  type        = string

  validation {
    condition     = length(var.project_id) > 0
    error_message = "Project ID must not be empty."
  }
}

variable "region" {
  description = "GCP region for resources"
  type        = string
  default     = "asia-southeast2" # Jakarta, Indonesia

  validation {
    condition     = can(regex("^[a-z]+-[a-z]+[0-9]$", var.region))
    error_message = "Region must be a valid GCP region format."
  }
}

variable "zone" {
  description = "GCP zone for compute instances"
  type        = string
  default     = "asia-southeast2-a"

  validation {
    condition     = can(regex("^[a-z]+-[a-z]+[0-9]-[a-z]$", var.zone))
    error_message = "Zone must be a valid GCP zone format."
  }
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

# -----------------------------------------------------------------------------
# Network Configuration
# -----------------------------------------------------------------------------

variable "vpc_name" {
  description = "Name of the VPC network"
  type        = string
  default     = "orange-wallet-vpc"
}

variable "subnet_cidr" {
  description = "CIDR block for the subnet"
  type        = string
  default     = "10.0.0.0/24"

  validation {
    condition     = can(cidrhost(var.subnet_cidr, 0))
    error_message = "Subnet CIDR must be a valid IPv4 CIDR block."
  }
}

variable "pod_cidr" {
  description = "CIDR block for Kubernetes pods (secondary range)"
  type        = string
  default     = "10.1.0.0/16"
}

variable "service_cidr" {
  description = "CIDR block for Kubernetes services (secondary range)"
  type        = string
  default     = "10.2.0.0/16"
}

# -----------------------------------------------------------------------------
# Compute Configuration
# -----------------------------------------------------------------------------

variable "master_machine_type" {
  description = "Machine type for K3s master node"
  type        = string
  default     = "e2-standard-2" # 2 vCPU, 8GB RAM
}

variable "worker_stateless_machine_type" {
  description = "Machine type for stateless worker nodes"
  type        = string
  default     = "e2-medium" # 2 vCPU, 4GB RAM
}

variable "worker_stateful_machine_type" {
  description = "Machine type for stateful worker node"
  type        = string
  default     = "e2-standard-2" # 2 vCPU, 8GB RAM
}

variable "master_disk_size" {
  description = "Boot disk size for master node (GB)"
  type        = number
  default     = 50
}

variable "worker_disk_size" {
  description = "Boot disk size for worker nodes (GB)"
  type        = number
  default     = 50
}

variable "ssh_user" {
  description = "SSH username for VM access"
  type        = string
  default     = "ubuntu"
}

variable "ssh_public_key_path" {
  description = "Path to SSH public key file"
  type        = string
  default     = "~/.ssh/orange-wallet-key.pub"
}

# -----------------------------------------------------------------------------
# Storage Configuration
# -----------------------------------------------------------------------------

variable "backup_bucket_name" {
  description = "Name of GCS bucket for database backups"
  type        = string
  default     = "orange-wallet-backups"
}

variable "backup_retention_days" {
  description = "Number of days to retain backups"
  type        = number
  default     = 30

  validation {
    condition     = var.backup_retention_days > 0 && var.backup_retention_days <= 365
    error_message = "Backup retention must be between 1 and 365 days."
  }
}

# -----------------------------------------------------------------------------
# Service Account Configuration
# -----------------------------------------------------------------------------

variable "k3s_node_sa_email" {
  description = "Email of the K3s node service account"
  type        = string
  default     = "" # Will be constructed if empty: k3s-node-sa@PROJECT_ID.iam.gserviceaccount.com
}

# -----------------------------------------------------------------------------
# Tagging and Labels
# -----------------------------------------------------------------------------

variable "labels" {
  description = "Common labels to apply to all resources"
  type        = map(string)
  default = {
    project     = "orange-wallet"
    environment = "dev"
    managed-by  = "terraform"
  }
}

# -----------------------------------------------------------------------------
# Feature Flags
# -----------------------------------------------------------------------------

variable "enable_cloud_nat" {
  description = "Enable Cloud NAT for private instances"
  type        = bool
  default     = false
}

variable "enable_monitoring" {
  description = "Enable GCP Cloud Monitoring integration"
  type        = bool
  default     = true
}

variable "enable_logging" {
  description = "Enable GCP Cloud Logging integration"
  type        = bool
  default     = true
}
