# =============================================================================
# Network Module - VPC, Subnets, Firewall Rules
# =============================================================================
#
# This module creates:
# - VPC network with custom subnet
# - Secondary IP ranges for pods and services
# - Firewall rules for K3s cluster communication
# - Optional Cloud NAT for private instances
#
# =============================================================================

# -----------------------------------------------------------------------------
# VPC Network
# -----------------------------------------------------------------------------

resource "google_compute_network" "vpc" {
  name                    = var.vpc_name
  auto_create_subnetworks = false
  routing_mode            = "REGIONAL"
  mtu                     = 1460

  project = var.project_id
}

# -----------------------------------------------------------------------------
# Subnet with Secondary Ranges
# -----------------------------------------------------------------------------

resource "google_compute_subnetwork" "subnet" {
  name          = "${var.vpc_name}-subnet-${var.region}"
  ip_cidr_range = var.subnet_cidr
  region        = var.region
  network       = google_compute_network.vpc.id

  # Secondary IP ranges for Kubernetes
  secondary_ip_range {
    range_name    = "pods"
    ip_cidr_range = var.pod_cidr
  }

  secondary_ip_range {
    range_name    = "services"
    ip_cidr_range = var.service_cidr
  }

  # Enable VPC Flow Logs for network visibility
  log_config {
    aggregation_interval = "INTERVAL_5_SEC"
    flow_sampling        = 0.5
    metadata             = "INCLUDE_ALL_METADATA"
  }

  private_ip_google_access = true

  project = var.project_id
}

# -----------------------------------------------------------------------------
# Firewall Rules
# -----------------------------------------------------------------------------

# Allow internal communication between all nodes
resource "google_compute_firewall" "allow_internal" {
  name    = "${var.vpc_name}-allow-internal"
  network = google_compute_network.vpc.name
  project = var.project_id

  allow {
    protocol = "tcp"
    ports    = ["0-65535"]
  }

  allow {
    protocol = "udp"
    ports    = ["0-65535"]
  }

  allow {
    protocol = "icmp"
  }

  source_ranges = [var.subnet_cidr, var.pod_cidr, var.service_cidr]

  priority = 1000
}

# Allow SSH from anywhere (restrict in production)
resource "google_compute_firewall" "allow_ssh" {
  name    = "${var.vpc_name}-allow-ssh"
  network = google_compute_network.vpc.name
  project = var.project_id

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["0.0.0.0/0"] # TODO: Restrict to specific IPs in production

  priority = 1000

  target_tags = ["k3s-node"]
}

# Allow K3s API server access
resource "google_compute_firewall" "allow_k3s_api" {
  name    = "${var.vpc_name}-allow-k3s-api"
  network = google_compute_network.vpc.name
  project = var.project_id

  allow {
    protocol = "tcp"
    ports    = ["6443"] # K3s API server
  }

  source_ranges = ["0.0.0.0/0"] # TODO: Restrict to specific IPs in production

  priority = 1000

  target_tags = ["k3s-master"]
}

# Allow HTTP/HTTPS traffic to load balancer
resource "google_compute_firewall" "allow_http_https" {
  name    = "${var.vpc_name}-allow-http-https"
  network = google_compute_network.vpc.name
  project = var.project_id

  allow {
    protocol = "tcp"
    ports    = ["80", "443"]
  }

  source_ranges = ["0.0.0.0/0"]

  priority = 1000

  target_tags = ["k3s-worker"]
}

# Allow health checks and load balancer traffic from GCP
resource "google_compute_firewall" "allow_health_checks" {
  name    = "${var.vpc_name}-allow-health-checks"
  network = google_compute_network.vpc.name
  project = var.project_id

  allow {
    protocol = "tcp"
    ports    = ["80", "443", "30254"]  # HTTP, HTTPS, Health check endpoint
  }

  # GCP health check and load balancer source ranges
  source_ranges = [
    "35.191.0.0/16",      # GCP Health Checks & LB
    "130.211.0.0/22",     # GCP Health Checks & LB (legacy)
    "209.85.152.0/22",    # GCP Health Checks
    "209.85.204.0/22"     # GCP Health Checks
  ]

  priority = 1000

  target_tags = ["k3s-worker"]
}

# Allow OpenVPN access for VPN clients
resource "google_compute_firewall" "allow_openvpn" {
  name    = "${var.vpc_name}-allow-openvpn"
  network = google_compute_network.vpc.name
  project = var.project_id

  allow {
    protocol = "udp"
    ports    = ["31194"] # OpenVPN NodePort
  }

  source_ranges = ["0.0.0.0/0"] # TODO: Restrict to specific IPs if needed

  priority = 1000

  target_tags = ["k3s-master"] # OpenVPN runs on master node
}

# -----------------------------------------------------------------------------
# Cloud NAT (Optional)
# -----------------------------------------------------------------------------

resource "google_compute_router" "nat_router" {
  count   = var.enable_cloud_nat ? 1 : 0
  name    = "${var.vpc_name}-nat-router"
  region  = var.region
  network = google_compute_network.vpc.id
  project = var.project_id
}

resource "google_compute_router_nat" "nat" {
  count  = var.enable_cloud_nat ? 1 : 0
  name   = "${var.vpc_name}-nat"
  router = google_compute_router.nat_router[0].name
  region = var.region

  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"

  log_config {
    enable = true
    filter = "ERRORS_ONLY"
  }

  project = var.project_id
}
