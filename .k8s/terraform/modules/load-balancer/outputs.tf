# Load Balancer Module Outputs

output "ingress_ip" {
  description = "External IP address for ingress"
  value       = google_compute_global_address.ingress.address
}

output "ingress_ip_name" {
  description = "Name of the reserved static IP"
  value       = google_compute_global_address.ingress.name
}

output "instance_group_name" {
  description = "Name of the instance group"
  value       = google_compute_instance_group.workers.name
}

output "health_check_name" {
  description = "Name of the health check"
  value       = google_compute_health_check.nodeport_http.name
}

output "backend_service_name" {
  description = "Name of the backend service"
  value       = google_compute_backend_service.ingress.name
}
