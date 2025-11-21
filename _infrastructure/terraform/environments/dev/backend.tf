# =============================================================================
# Terraform Backend - GCS Remote State
# =============================================================================
#
# IMPORTANT: Before using this backend:
# 1. Run bootstrap first: cd ../../bootstrap && terraform apply ✅ DONE
# 2. Ensure bucket exists: orange-wallet-tf-state-275033978165 ✅ DONE
# 3. Run: terraform init (first time setup)
#
# =============================================================================

terraform {
  backend "gcs" {
    bucket = "orange-wallet-tf-state-275033978165"
    prefix = "dev/infrastructure"

    # GCS backend provides automatic state locking
    # No additional configuration needed
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
