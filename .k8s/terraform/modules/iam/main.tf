# IAM Module - Service Accounts and Permissions for K3s Nodes

# Service Account for K3s nodes
resource "google_service_account" "k3s_nodes" {
  account_id   = "${var.project_name}-${var.environment}-k3s-sa"
  display_name = "Service Account for ${var.project_name} K3s Nodes"
  description  = "Service account used by K3s master and worker nodes"
  project      = var.project_id
}

# IAM Role Bindings for the service account
# Logging - allows nodes to write logs to Cloud Logging
resource "google_project_iam_member" "logging_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.k3s_nodes.email}"
}

# Monitoring - allows nodes to write metrics to Cloud Monitoring
resource "google_project_iam_member" "monitoring_writer" {
  project = var.project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.k3s_nodes.email}"
}

# Compute Viewer - allows nodes to query instance metadata
resource "google_project_iam_member" "compute_viewer" {
  project = var.project_id
  role    = "roles/compute.viewer"
  member  = "serviceAccount:${google_service_account.k3s_nodes.email}"
}

# Storage Object Viewer - allows reading from GCS buckets (for backups, artifacts, etc.)
resource "google_project_iam_member" "storage_viewer" {
  project = var.project_id
  role    = "roles/storage.objectViewer"
  member  = "serviceAccount:${google_service_account.k3s_nodes.email}"
}

# Optional: Storage Object Admin for Ceph backups to GCS
# Uncomment if you want to enable GCS backups
# resource "google_project_iam_member" "storage_admin" {
#   project = var.project_id
#   role    = "roles/storage.objectAdmin"
#   member  = "serviceAccount:${google_service_account.k3s_nodes.email}"
# }
