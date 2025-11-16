# =============================================================================
# Load Balancer Module - External HTTP(S) Load Balancer
# =============================================================================
#
# This module creates:
# - Global external IP address
# - HTTP(S) load balancer with backend service
# - Health checks for worker nodes
# - SSL certificate support (future)
#
# =============================================================================

# -----------------------------------------------------------------------------
# External IP Address
# -----------------------------------------------------------------------------

resource "google_compute_global_address" "lb_ip" {
  name         = "${var.environment}-orange-wallet-lb-ip"
  address_type = "EXTERNAL"
  project      = var.project_id
}

# -----------------------------------------------------------------------------
# Health Check
# -----------------------------------------------------------------------------

resource "google_compute_health_check" "http_health_check" {
  name                = "${var.environment}-orange-wallet-http-health-check"
  check_interval_sec  = 10
  timeout_sec         = 5
  healthy_threshold   = 2
  unhealthy_threshold = 3
  project             = var.project_id

  http_health_check {
    port         = 80
    request_path = "/healthz"
  }
}

# -----------------------------------------------------------------------------
# Backend Service
# -----------------------------------------------------------------------------

resource "google_compute_backend_service" "default" {
  name                  = "${var.environment}-orange-wallet-backend"
  protocol              = "HTTP"
  port_name             = "http"
  timeout_sec           = 30
  enable_cdn            = false
  health_checks         = [google_compute_health_check.http_health_check.id]
  load_balancing_scheme = "EXTERNAL"
  project               = var.project_id

  backend {
    group           = var.worker_instance_groups["workers"]
    balancing_mode  = "UTILIZATION"
    capacity_scaler = 1.0
  }

  log_config {
    enable      = true
    sample_rate = 1.0
  }
}

# -----------------------------------------------------------------------------
# URL Map
# -----------------------------------------------------------------------------

resource "google_compute_url_map" "default" {
  name            = "${var.environment}-orange-wallet-url-map"
  default_service = google_compute_backend_service.default.id
  project         = var.project_id
}

# -----------------------------------------------------------------------------
# HTTP Proxy
# -----------------------------------------------------------------------------

resource "google_compute_target_http_proxy" "default" {
  name    = "${var.environment}-orange-wallet-http-proxy"
  url_map = google_compute_url_map.default.id
  project = var.project_id
}

# -----------------------------------------------------------------------------
# Forwarding Rule
# -----------------------------------------------------------------------------

resource "google_compute_global_forwarding_rule" "http" {
  name                  = "${var.environment}-orange-wallet-http-forwarding-rule"
  target                = google_compute_target_http_proxy.default.id
  port_range            = "80"
  ip_address            = google_compute_global_address.lb_ip.address
  load_balancing_scheme = "EXTERNAL"
  project               = var.project_id
}

# -----------------------------------------------------------------------------
# HTTPS Support (Optional - Commented for initial setup)
# -----------------------------------------------------------------------------

# Uncomment when you have SSL certificate ready

# resource "google_compute_ssl_certificate" "default" {
#   name_prefix = "${var.environment}-orange-wallet-cert-"
#   private_key = file("path/to/private.key")
#   certificate = file("path/to/certificate.crt")
#   project     = var.project_id
#
#   lifecycle {
#     create_before_destroy = true
#   }
# }

# resource "google_compute_target_https_proxy" "default" {
#   name             = "${var.environment}-orange-wallet-https-proxy"
#   url_map          = google_compute_url_map.default.id
#   ssl_certificates = [google_compute_ssl_certificate.default.id]
#   project          = var.project_id
# }

# resource "google_compute_global_forwarding_rule" "https" {
#   name                  = "${var.environment}-orange-wallet-https-forwarding-rule"
#   target                = google_compute_target_https_proxy.default.id
#   port_range            = "443"
#   ip_address            = google_compute_global_address.lb_ip.address
#   load_balancing_scheme = "EXTERNAL"
#   project               = var.project_id
# }
