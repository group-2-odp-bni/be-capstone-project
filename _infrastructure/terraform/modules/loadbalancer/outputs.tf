# =============================================================================
# Load Balancer Module - Outputs
# =============================================================================

output "external_ip" {
  description = "External IP address of the load balancer"
  value       = google_compute_address.lb_ip.address
}

output "load_balancer_url" {
  description = "URL to access the load balancer"
  value       = "http://${google_compute_address.lb_ip.address}"
}
