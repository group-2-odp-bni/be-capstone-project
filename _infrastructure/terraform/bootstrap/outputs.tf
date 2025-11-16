# =============================================================================
# Terraform Bootstrap - Outputs
# =============================================================================

output "state_bucket_name" {
  description = "Name of the created Terraform state bucket"
  value       = google_storage_bucket.terraform_state.name
}

output "state_bucket_url" {
  description = "GCS URL of the Terraform state bucket"
  value       = google_storage_bucket.terraform_state.url
}

output "state_bucket_location" {
  description = "Location of the Terraform state bucket"
  value       = google_storage_bucket.terraform_state.location
}

output "versioning_enabled" {
  description = "Whether versioning is enabled on the state bucket"
  value       = google_storage_bucket.terraform_state.versioning[0].enabled
}

output "instructions" {
  description = "Next steps after bootstrap"
  value = <<-EOT

    ✅ Terraform state bucket created successfully!

    Bucket Name: ${google_storage_bucket.terraform_state.name}
    Location: ${google_storage_bucket.terraform_state.location}
    Versioning: Enabled

    📋 NEXT STEPS:

    1. Add this backend configuration to your main Terraform:

       terraform {
         backend "gcs" {
           bucket = "${google_storage_bucket.terraform_state.name}"
           prefix = "production/k3s"
         }
       }

    2. Navigate to main infrastructure:
       cd ../environments/dev

    3. Initialize Terraform with remote backend:
       terraform init

    4. Verify backend migration:
       terraform state list

    5. Never delete this bucket manually!
       It contains your infrastructure state.

    ⚠️  IMPORTANT:
    - Keep this bucket versioned (already enabled)
    - Enable Object Versioning in GCP Console if needed
    - Regularly backup state: terraform state pull > backup.tfstate

  EOT
}
