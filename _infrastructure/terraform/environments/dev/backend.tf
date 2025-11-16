# =============================================================================
# Terraform Backend - GCS Remote State
# =============================================================================
#
# IMPORTANT: Before using this backend:
# 1. Run bootstrap first: cd ../../bootstrap && terraform apply
# 2. Ensure bucket "orange-wallet-tf-state" exists
# 3. Run: terraform init -migrate-state (if migrating from local)
#
# =============================================================================

terraform {
  backend "gcs" {
    bucket = "orange-wallet-tf-state"
    prefix = "production/k3s"

    # Optional: Enable state locking (requires additional setup)
    # See: https://www.terraform.io/language/settings/backends/gcs#configuration-variables
  }
}

# =============================================================================
# State Locking
# =============================================================================
#
# GCS backend provides automatic state locking without additional configuration.
# State file is locked during operations to prevent concurrent modifications.
#
# To view state lock status:
#   terraform force-unlock <lock-id>
#
# =============================================================================
