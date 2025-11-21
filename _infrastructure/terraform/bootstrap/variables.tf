# =============================================================================
# Terraform Bootstrap - Variables
# =============================================================================

variable "project_id" {
  description = "GCP Project ID where the state bucket will be created"
  type        = string

  validation {
    condition     = length(var.project_id) > 0
    error_message = "Project ID must not be empty."
  }
}

variable "region" {
  description = "GCP region for the state bucket (use same region as infrastructure)"
  type        = string
  default     = "asia-southeast2" # Jakarta region

  validation {
    condition     = can(regex("^[a-z]+-[a-z]+[0-9]$", var.region))
    error_message = "Region must be a valid GCP region format (e.g., asia-southeast2)."
  }
}

variable "bucket_name" {
  description = "Name of the GCS bucket for Terraform state (must be globally unique)"
  type        = string
  default     = "orange-wallet-tf-state"

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]*[a-z0-9]$", var.bucket_name)) && length(var.bucket_name) >= 3 && length(var.bucket_name) <= 63
    error_message = "Bucket name must be 3-63 characters, lowercase letters, numbers, and hyphens only."
  }
}

# Optional: KMS key for encryption
# variable "kms_key_name" {
#   description = "KMS key name for bucket encryption (optional, uses Google-managed by default)"
#   type        = string
#   default     = ""
# }

# Optional: Service account for Terraform
# variable "terraform_sa_email" {
#   description = "Service account email for Terraform (optional)"
#   type        = string
#   default     = ""
# }
