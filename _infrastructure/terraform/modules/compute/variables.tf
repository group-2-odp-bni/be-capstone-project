# =============================================================================
# Compute Module - Variables
# =============================================================================

variable "project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "region" {
  description = "GCP region"
  type        = string
}

variable "zone" {
  description = "GCP zone for compute instances"
  type        = string
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
}

variable "network_name" {
  description = "Name of the VPC network"
  type        = string
}

variable "subnet_name" {
  description = "Name of the subnet"
  type        = string
}

variable "subnet_self_link" {
  description = "Self-link of the subnet"
  type        = string
}

variable "master_machine_type" {
  description = "Machine type for K3s master node"
  type        = string
}

variable "worker_stateless_machine_type" {
  description = "Machine type for stateless worker nodes"
  type        = string
}

variable "worker_stateful_machine_type" {
  description = "Machine type for stateful worker node"
  type        = string
}

variable "master_disk_size" {
  description = "Boot disk size for master node (GB)"
  type        = number
}

variable "worker_disk_size" {
  description = "Boot disk size for worker nodes (GB)"
  type        = number
}

variable "ssh_user" {
  description = "SSH username for VM access"
  type        = string
}

variable "ssh_public_key_path" {
  description = "Path to SSH public key file"
  type        = string
}

variable "service_account_email" {
  description = "Email of the service account to attach to instances"
  type        = string
}

variable "labels" {
  description = "Labels to apply to resources"
  type        = map(string)
  default     = {}
}
