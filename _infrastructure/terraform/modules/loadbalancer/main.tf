# =============================================================================
# Load Balancer Module - L4 External Network Load Balancer using Target Pool
# =============================================================================

# -----------------------------------------------------------------------------
# External IP Address (Regional, for Network LB)
# -----------------------------------------------------------------------------
resource "google_compute_address" "lb_ip" {
  name    = "${var.environment}-orange-wallet-lb-ip"
  project = var.project_id
  region  = var.region
}

# -----------------------------------------------------------------------------
# Health Check
# -----------------------------------------------------------------------------
# Using HTTP health check to NGINX Ingress Controller HTTP NodePort.
# NGINX Ingress returns 404 for root path (no default backend configured).
# 404 response means NGINX is alive and responding, which is sufficient for health check.
resource "google_compute_http_health_check" "ingress_health_check" {
  name    = "${var.environment}-ingress-http-health-check"
  project = var.project_id

  port         = 30254 # Health check endpoint NodePort
  request_path = "/healthz"

  timeout_sec         = 5
  check_interval_sec  = 10
  healthy_threshold   = 2
  unhealthy_threshold = 3
}

# -----------------------------------------------------------------------------
# Target Pool (A pool of our worker nodes)
# -----------------------------------------------------------------------------
resource "google_compute_target_pool" "ingress_pool" {
  name    = "${var.environment}-orange-wallet-ingress-pool"
  project = var.project_id
  region  = var.region

  instances = var.worker_instances_self_links
  health_checks = [
    google_compute_http_health_check.ingress_health_check.self_link,
  ]
}

# -----------------------------------------------------------------------------
# Forwarding Rules (Routes external IP:Port to the Target Pool)
# -----------------------------------------------------------------------------
resource "google_compute_forwarding_rule" "http" {
  name       = "${var.environment}-orange-wallet-http-forwarding-rule"
  project    = var.project_id
  region     = var.region
  ip_protocol = "TCP"
  port_range = "80"
  target     = google_compute_target_pool.ingress_pool.self_link
  ip_address = google_compute_address.lb_ip.address
}

resource "google_compute_forwarding_rule" "https" {
  name       = "${var.environment}-orange-wallet-https-forwarding-rule"
  project    = var.project_id
  region     = var.region
  ip_protocol = "TCP"
  port_range = "443"
  target     = google_compute_target_pool.ingress_pool.self_link
  ip_address = google_compute_address.lb_ip.address
}

