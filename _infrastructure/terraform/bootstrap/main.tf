# =============================================================================
# Terraform Bootstrap - GCS State Bucket Creation
# =============================================================================
# Purpose: Create GCS bucket for storing Terraform state
# Run once before main infrastructure deployment
# Uses local state (this creates the remote state bucket)
# =============================================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }

  # NOTE: This uses LOCAL state (no backend block)
  # The bucket created here will be used as backend for other Terraform configs
}

# =============================================================================
# Provider Configuration
# =============================================================================

provider "google" {
  project = var.project_id
  region  = var.region
}

# =============================================================================
# GCS Bucket for Terraform State
# =============================================================================

resource "google_storage_bucket" "terraform_state" {
  name          = var.bucket_name
  location      = var.region
  force_destroy = false # Prevent accidental deletion

  # Storage class
  storage_class = "STANDARD"

  # Versioning - CRITICAL for state management
  versioning {
    enabled = true
  }

  # Uniform bucket-level access (recommended)
  uniform_bucket_level_access = true

  # Lifecycle rules
  lifecycle_rule {
    # Keep deleted versions for 30 days (safety net)
    condition {
      age                = 30
      with_state         = "ARCHIVED"
    }
    action {
      type = "Delete"
    }
  }

  # Encryption (optional - uses Google-managed keys by default)
  # Uncomment to use customer-managed encryption key:
  # encryption {
  #   default_kms_key_name = var.kms_key_name
  # }

  # Labels
  labels = {
    purpose     = "terraform-state"
    environment = "all"
    managed-by  = "terraform"
    project     = "orange-wallet"
  }
}

# =============================================================================
# IAM Binding (Optional - if using service account)
# =============================================================================

# Grant Terraform service account access to bucket
# Uncomment if using dedicated service account for Terraform

# resource "google_storage_bucket_iam_member" "terraform_state_admin" {
#   bucket = google_storage_bucket.terraform_state.name
#   role   = "roles/storage.objectAdmin"
#   member = "serviceAccount:${var.terraform_sa_email}"
# }

# =============================================================================
# Outputs
# =============================================================================

# Output bucket name for reference
output "bucket_name" {
  description = "Name of the Terraform state bucket"
  value       = google_storage_bucket.terraform_state.name
}

output "bucket_url" {
  description = "GCS URL of the Terraform state bucket"
  value       = google_storage_bucket.terraform_state.url
}

output "backend_config" {
  description = "Backend configuration to use in main Terraform"
  value = <<-EOT
    terraform {
      backend "gcs" {
        bucket = "${google_storage_bucket.terraform_state.name}"
        prefix = "production/k3s"
      }
    }
  EOT
}
