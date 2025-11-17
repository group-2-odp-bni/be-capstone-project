# =============================================================================
# Storage Module - GCS Buckets
# =============================================================================
#
# This module creates:
# - Database backup bucket with lifecycle policies
# - Optional log archive bucket
#
# =============================================================================

locals {
  # Construct backup SA email if not provided
  backup_sa_email = var.backup_sa_email != "" ? var.backup_sa_email : "k8s-backup-sa@${var.project_id}.iam.gserviceaccount.com"
}

# -----------------------------------------------------------------------------
# Backup Bucket for PostgreSQL
# -----------------------------------------------------------------------------

resource "google_storage_bucket" "backup_bucket" {
  name          = var.backup_bucket_name
  location      = var.region
  force_destroy = false # Prevent accidental deletion
  project       = var.project_id

  uniform_bucket_level_access = true

  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      age = var.backup_retention_days
    }
    action {
      type = "Delete"
    }
  }

  lifecycle_rule {
    condition {
      age                = 7
      with_state         = "ARCHIVED"
    }
    action {
      type = "Delete"
    }
  }

  labels = merge(
    var.labels,
    {
      purpose = "database-backups"
      type    = "backup-storage"
    }
  )

  # Enable encryption (Google-managed by default, customer-managed if KMS key provided)
  dynamic "encryption" {
    for_each = var.kms_key_name != null ? [1] : []
    content {
      default_kms_key_name = var.kms_key_name
    }
  }
}

# -----------------------------------------------------------------------------
# IAM Binding for Backup Service Account
# -----------------------------------------------------------------------------

resource "google_storage_bucket_iam_member" "backup_writer" {
  bucket = google_storage_bucket.backup_bucket.name
  role   = "roles/storage.objectCreator"
  member = "serviceAccount:${local.backup_sa_email}"
}

resource "google_storage_bucket_iam_member" "backup_reader" {
  bucket = google_storage_bucket.backup_bucket.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${local.backup_sa_email}"
}

# -----------------------------------------------------------------------------
# Optional: Log Archive Bucket
# -----------------------------------------------------------------------------

resource "google_storage_bucket" "log_archive_bucket" {
  count         = var.enable_log_archive ? 1 : 0
  name          = "${var.backup_bucket_name}-logs"
  location      = var.region
  force_destroy = false
  project       = var.project_id

  uniform_bucket_level_access = true

  versioning {
    enabled = false # Logs don't need versioning
  }

  lifecycle_rule {
    condition {
      age = 90 # Keep logs for 90 days
    }
    action {
      type = "Delete"
    }
  }

  # Lifecycle transition to cheaper storage class
  lifecycle_rule {
    condition {
      age = 30
    }
    action {
      type          = "SetStorageClass"
      storage_class = "NEARLINE"
    }
  }

  labels = merge(
    var.labels,
    {
      purpose = "log-archive"
      type    = "log-storage"
    }
  )
}
