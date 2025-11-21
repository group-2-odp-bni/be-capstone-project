# =============================================================================
# Load Balancer Module - Variables
# =============================================================================

variable "project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "region" {
  description = "GCP region"
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

variable "worker_instances_self_links" {
  description = "List of self_links for the worker compute instances"
  type        = list(string)
}

variable "worker_nodes_tag" {
  description = "The network tag applied to all worker nodes"
  type        = string
}

variable "labels" {
  description = "Labels to apply to resources"
  type        = map(string)
  default     = {}
}
