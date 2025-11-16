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

variable "worker_instance_groups" {
  description = "Map of instance groups for backend services"
  type        = map(string)
}

variable "labels" {
  description = "Labels to apply to resources"
  type        = map(string)
  default     = {}
}

variable "enable_cdn" {
  description = "Enable Cloud CDN on backend service"
  type        = bool
  default     = false
}

variable "enable_ssl" {
  description = "Enable HTTPS with SSL certificate"
  type        = bool
  default     = false
}
