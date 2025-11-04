# GCP Load Balancer Module
# Implements External TCP/SSL Proxy Load Balancer for K3s Ingress (NodePort)
# Following GCP best practices for self-managed Kubernetes clusters

# Reserve Static External IP
resource "google_compute_global_address" "ingress" {
  name        = "${var.project_name}-${var.environment}-ingress-ip"
  project     = var.project_id
  description = "Static external IP for ingress load balancer"
}

# Create Unmanaged Instance Group for Worker Nodes
resource "google_compute_instance_group" "workers" {
  name        = "${var.project_name}-${var.environment}-workers"
  description = "Instance group for K3s worker nodes"
  zone        = var.zone
  project     = var.project_id

  instances = var.worker_instance_self_links

  named_port {
    name = "http"
    port = 30080 # NodePort from Nginx Ingress
  }

  named_port {
    name = "https"
    port = 30443 # NodePort from Nginx Ingress
  }
}

# Health Check for NodePort HTTP
resource "google_compute_health_check" "nodeport_http" {
  name                = "${var.project_name}-${var.environment}-nodeport-http-hc"
  check_interval_sec  = 10
  timeout_sec         = 5
  healthy_threshold   = 2
  unhealthy_threshold = 3
  project             = var.project_id

  http_health_check {
    port         = 30080
    request_path = "/healthz" # Nginx Ingress health endpoint
  }
}

# Backend Service for HTTPS
resource "google_compute_backend_service" "ingress" {
  name                  = "${var.project_name}-${var.environment}-ingress-backend"
  protocol              = "TCP"
  port_name             = "https"
  timeout_sec           = 30
  health_checks         = [google_compute_health_check.nodeport_http.id]
  load_balancing_scheme = "EXTERNAL"
  project               = var.project_id

  backend {
    group           = google_compute_instance_group.workers.id
    balancing_mode  = "CONNECTION"
    max_connections = 1000
  }
}

# Target TCP Proxy for HTTPS (443)
resource "google_compute_target_tcp_proxy" "ingress_https" {
  name            = "${var.project_name}-${var.environment}-ingress-proxy-https"
  backend_service = google_compute_backend_service.ingress.id
  project         = var.project_id
}

# Global Forwarding Rule for HTTPS (443)
resource "google_compute_global_forwarding_rule" "ingress_https" {
  name       = "${var.project_name}-${var.environment}-ingress-https"
  target     = google_compute_target_tcp_proxy.ingress_https.id
  port_range = "443"
  ip_address = google_compute_global_address.ingress.address
  project    = var.project_id
}

# Backend Service for HTTP (can redirect to HTTPS at Ingress level)
resource "google_compute_backend_service" "ingress_http" {
  name                  = "${var.project_name}-${var.environment}-ingress-backend-http"
  protocol              = "TCP"
  port_name             = "http"
  timeout_sec           = 30
  health_checks         = [google_compute_health_check.nodeport_http.id]
  load_balancing_scheme = "EXTERNAL"
  project               = var.project_id

  backend {
    group           = google_compute_instance_group.workers.id
    balancing_mode  = "CONNECTION"
    max_connections = 1000
  }
}

# Target TCP Proxy for HTTP (80)
resource "google_compute_target_tcp_proxy" "ingress_http" {
  name            = "${var.project_name}-${var.environment}-ingress-proxy-http"
  backend_service = google_compute_backend_service.ingress_http.id
  project         = var.project_id
}

# Global Forwarding Rule for HTTP (80)
resource "google_compute_global_forwarding_rule" "ingress_http" {
  name       = "${var.project_name}-${var.environment}-ingress-http"
  target     = google_compute_target_tcp_proxy.ingress_http.id
  port_range = "80"
  ip_address = google_compute_global_address.ingress.address
  project    = var.project_id
}
