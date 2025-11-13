# Network Module - VPC, Subnets, and Firewall Rules
# Provisions networking infrastructure for K3s cluster

# VPC Network
resource "google_compute_network" "vpc" {
  name                    = "${var.project_name}-${var.environment}-vpc"
  auto_create_subnetworks = false
  description             = "VPC network for ${var.project_name} K3s cluster"
  project                 = var.project_id
}

# Subnet for K3s nodes
resource "google_compute_subnetwork" "subnet" {
  name          = "${var.project_name}-${var.environment}-subnet"
  ip_cidr_range = var.subnet_cidr
  region        = var.region
  network       = google_compute_network.vpc.id
  description   = "Subnet for K3s nodes"
  project       = var.project_id

  # Enable flow logs for network monitoring (optional)
  log_config {
    aggregation_interval = "INTERVAL_5_SEC"
    flow_sampling        = 0.5
    metadata             = "INCLUDE_ALL_METADATA"
  }
}

# Firewall Rule - Allow SSH from anywhere
resource "google_compute_firewall" "allow_ssh" {
  name    = "${var.project_name}-${var.environment}-allow-ssh"
  network = google_compute_network.vpc.id
  project = var.project_id

  description = "Allow SSH access to all VMs"

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["k3s-node"]

  priority = 1000
}

# Firewall Rule - Allow internal communication between nodes
resource "google_compute_firewall" "allow_internal" {
  name    = "${var.project_name}-${var.environment}-allow-internal"
  network = google_compute_network.vpc.id
  project = var.project_id

  description = "Allow all internal communication between K3s nodes"

  allow {
    protocol = "tcp"
  }

  allow {
    protocol = "udp"
  }

  allow {
    protocol = "icmp"
  }

  source_tags = ["k3s-node"]
  target_tags = ["k3s-node"]

  priority = 1000
}

# Firewall Rule - Allow K3s API server access (6443)
resource "google_compute_firewall" "allow_k3s_api" {
  name    = "${var.project_name}-${var.environment}-allow-k3s-api"
  network = google_compute_network.vpc.id
  project = var.project_id

  description = "Allow access to K3s API server"

  allow {
    protocol = "tcp"
    ports    = ["6443"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["k3s-master"]

  priority = 1000
}

# Firewall Rule - Allow HTTP/HTTPS traffic for Ingress
resource "google_compute_firewall" "allow_http_https" {
  name    = "${var.project_name}-${var.environment}-allow-http-https"
  network = google_compute_network.vpc.id
  project = var.project_id

  description = "Allow HTTP and HTTPS traffic for ingress controller"

  allow {
    protocol = "tcp"
    ports    = ["80", "443"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["k3s-node"]

  priority = 1000
}

# Firewall Rule - Allow health checks from GCP
resource "google_compute_firewall" "allow_health_checks" {
  name    = "${var.project_name}-${var.environment}-allow-health-checks"
  network = google_compute_network.vpc.id
  project = var.project_id

  description = "Allow health checks from GCP load balancers"

  allow {
    protocol = "tcp"
  }

  # GCP health check IP ranges
  source_ranges = [
    "35.191.0.0/16",
    "130.211.0.0/22"
  ]

  target_tags = ["k3s-node"]

  priority = 1000
}

# Firewall Rule - Allow NodePort services (30000-32767)
resource "google_compute_firewall" "allow_nodeports" {
  name    = "${var.project_name}-${var.environment}-allow-nodeports"
  network = google_compute_network.vpc.id
  project = var.project_id

  description = "Allow Kubernetes NodePort services"

  allow {
    protocol = "tcp"
    ports    = ["30000-32767"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["k3s-node"]

  priority = 1000
}

# Firewall Rule - Allow Flannel VXLAN (K3s default CNI)
resource "google_compute_firewall" "allow_flannel" {
  name    = "${var.project_name}-${var.environment}-allow-flannel"
  network = google_compute_network.vpc.id
  project = var.project_id

  description = "Allow Flannel VXLAN traffic for pod networking"

  allow {
    protocol = "udp"
    ports    = ["8472"]
  }

  source_tags = ["k3s-node"]
  target_tags = ["k3s-node"]

  priority = 1000
}

# Firewall Rule - Allow Ceph communication
resource "google_compute_firewall" "allow_ceph" {
  name    = "${var.project_name}-${var.environment}-allow-ceph"
  network = google_compute_network.vpc.id
  project = var.project_id

  description = "Allow Ceph storage communication between nodes"

  allow {
    protocol = "tcp"
    ports    = ["6789", "6800-7300"]
  }

  source_tags = ["k3s-node"]
  target_tags = ["k3s-node"]

  priority = 1000
}

# Cloud Router for NAT (if private IPs are used)
resource "google_compute_router" "router" {
  name    = "${var.project_name}-${var.environment}-router"
  region  = var.region
  network = google_compute_network.vpc.id
  project = var.project_id

  bgp {
    asn = 64514
  }
}

# Cloud NAT for outbound internet access from private IPs
resource "google_compute_router_nat" "nat" {
  name                               = "${var.project_name}-${var.environment}-nat"
  router                             = google_compute_router.router.name
  region                             = var.region
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
  project                            = var.project_id

  log_config {
    enable = true
    filter = "ERRORS_ONLY"
  }
}
