# Compute Module Variables

variable "project_id" {
  description = "GCP project ID"
  type        = string
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "region" {
  description = "GCP region"
  type        = string
}

variable "zone" {
  description = "GCP zone"
  type        = string
}

variable "vpc_network_id" {
  description = "ID of the VPC network"
  type        = string
}

variable "vpc_network_name" {
  description = "Name of the VPC network"
  type        = string
}

variable "subnet_id" {
  description = "ID of the subnet"
  type        = string
}

variable "subnet_cidr" {
  description = "CIDR range of the subnet"
  type        = string
}

variable "service_account_email" {
  description = "Email of the service account"
  type        = string
}

variable "master_machine_type" {
  description = "Machine type for master node"
  type        = string
}

variable "master_disk_size" {
  description = "Disk size for master node in GB"
  type        = number
}

variable "master_disk_type" {
  description = "Disk type for master node"
  type        = string
}

variable "worker_count" {
  description = "Number of worker nodes to create"
  type        = number
  default     = 1
}

variable "worker_machine_type" {
  description = "Machine type for worker node"
  type        = string
}

variable "worker_disk_size" {
  description = "Disk size for worker node in GB"
  type        = number
}

variable "worker_disk_type" {
  description = "Disk type for worker node"
  type        = string
}

variable "k3s_version" {
  description = "K3s version to install"
  type        = string
}

variable "k3s_token" {
  description = "K3s cluster token"
  type        = string
  sensitive   = true
}



variable "ssh_public_key_path" {
  description = "Path to SSH public key"
  type        = string
}

variable "ssh_user" {
  description = "SSH username"
  type        = string
}

variable "enable_preemptible_vms" {
  description = "Enable preemptible VMs"
  type        = bool
}

variable "enable_monitoring" {
  description = "Enable GCP monitoring"
  type        = bool
}

variable "enable_external_ip" {
  description = "Enable external IP addresses"
  type        = bool
}

variable "use_static_ip_for_master" {
  description = "Use static IP for master node (recommended for VPN stability)"
  type        = bool
  default     = true
}

variable "labels" {
  description = "Labels for resources"
  type        = map(string)
  default     = {}
}
