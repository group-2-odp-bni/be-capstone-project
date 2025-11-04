# Load Balancer Module Variables

variable "project_id" {
  description = "GCP project ID"
  type        = string
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
}

variable "zone" {
  description = "GCP zone for instance group"
  type        = string
}

variable "worker_instance_self_links" {
  description = "List of self links for worker instances to include in the instance group"
  type        = list(string)
}
