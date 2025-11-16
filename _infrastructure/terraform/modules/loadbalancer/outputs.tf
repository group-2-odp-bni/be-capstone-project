# =============================================================================
# Load Balancer Module - Outputs
# =============================================================================

output "external_ip" {
  description = "External IP address of the load balancer"
  value       = google_compute_global_address.lb_ip.address
}

output "external_ip_name" {
  description = "Name of the external IP resource"
  value       = google_compute_global_address.lb_ip.name
}

output "backend_service_id" {
  description = "ID of the backend service"
  value       = google_compute_backend_service.default.id
}

output "url_map_id" {
  description = "ID of the URL map"
  value       = google_compute_url_map.default.id
}

output "health_check_id" {
  description = "ID of the health check"
  value       = google_compute_health_check.http_health_check.id
}

output "load_balancer_url" {
  description = "URL to access the load balancer"
  value       = "http://${google_compute_global_address.lb_ip.address}"
}
