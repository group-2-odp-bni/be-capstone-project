# IAM Module Outputs

output "service_account_email" {
  description = "Email of the K3s service account"
  value       = google_service_account.k3s_nodes.email
}

output "service_account_id" {
  description = "ID of the K3s service account"
  value       = google_service_account.k3s_nodes.id
}

output "service_account_name" {
  description = "Name of the K3s service account"
  value       = google_service_account.k3s_nodes.name
}
