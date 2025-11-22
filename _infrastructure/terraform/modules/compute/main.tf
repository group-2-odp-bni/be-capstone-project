# =============================================================================
# Compute Module - K3s Cluster Nodes
# =============================================================================
#
# This module creates:
# - 1 Master node (K3s control plane)
# - 2 Stateless worker nodes
# - 1 Stateful worker node
#
# Node Architecture:
# - Master: e2-standard-2 (2 vCPU, 8GB RAM) - Control plane + etcd
# - Worker-1: e2-medium (2 vCPU, 4GB RAM) - Stateless workloads
# - Worker-2: e2-medium (2 vCPU, 4GB RAM) - Stateless workloads
# - Worker-3: e2-standard-2 (2 vCPU, 8GB RAM) - Stateful workloads
#
# =============================================================================

locals {
  # Common instance metadata
  common_metadata = {
    enable-oslogin = "FALSE"
    ssh-keys       = "${var.ssh_user}:${file(var.ssh_public_key_path)}"
  }

  # Common instance tags
  common_tags = ["k3s-node"]

  # Worker nodes configuration
  worker_configs = {
    worker-1 = {
      name         = "orange-wallet-worker-1"
      machine_type = "e2-standard-2"  # Upgraded from e2-medium (4GB → 8GB) for notification-worker
      workload     = "stateless"
      tags         = concat(local.common_tags, ["k3s-worker", "stateless"])
    }
    worker-2 = {
      name         = "orange-wallet-worker-2"
      machine_type = "e2-standard-2"  # Upgraded from e2-medium (4GB → 8GB) for balanced stateless capacity
      workload     = "stateless"
      tags         = concat(local.common_tags, ["k3s-worker", "stateless"])
    }
    worker-3 = {
      name         = "orange-wallet-worker-3"
      machine_type = var.worker_stateful_machine_type
      workload     = "stateful"
      tags         = concat(local.common_tags, ["k3s-worker", "stateful"])
    }
  }
}

# -----------------------------------------------------------------------------
# Master Node
# -----------------------------------------------------------------------------

resource "google_compute_address" "master_external_ip" {
  name         = "orange-wallet-master-ip"
  address_type = "EXTERNAL"
  region       = var.region
  project      = var.project_id
}

resource "google_compute_instance" "master" {
  name         = "orange-wallet-master"
  machine_type = var.master_machine_type
  zone         = var.zone
  project      = var.project_id

  tags = concat(local.common_tags, ["k3s-master"])

  labels = merge(
    var.labels,
    {
      node-role     = "master"
      workload-type = "control-plane"
    }
  )

  boot_disk {
    initialize_params {
      image = "ubuntu-os-cloud/ubuntu-2204-lts"
      size  = var.master_disk_size
      type  = "pd-ssd"
    }
  }

  network_interface {
    subnetwork = var.subnet_self_link

    access_config {
      nat_ip = google_compute_address.master_external_ip.address
    }
  }

  metadata = merge(
    local.common_metadata,
    {
      user-data = templatefile("${path.module}/templates/cloud-init-master.yaml", {
        hostname = "orange-wallet-master"
      })
    }
  )

  service_account {
    email  = var.service_account_email
    scopes = ["cloud-platform"]
  }

  # Enable IP forwarding for VPN and network routing
  # CRITICAL: Required for OpenVPN, K3s Flannel, and pod networking
  can_ip_forward = true

  allow_stopping_for_update = true

  lifecycle {
    ignore_changes = [metadata["ssh-keys"]]
  }
}

# -----------------------------------------------------------------------------
# Worker Nodes
# -----------------------------------------------------------------------------

resource "google_compute_address" "worker_external_ip" {
  for_each = local.worker_configs

  name         = "${each.value.name}-ip"
  address_type = "EXTERNAL"
  region       = var.region
  project      = var.project_id
}

resource "google_compute_instance" "worker" {
  for_each = local.worker_configs

  name         = each.value.name
  machine_type = each.value.machine_type
  zone         = var.zone
  project      = var.project_id

  tags = each.value.tags

  labels = merge(
    var.labels,
    {
      node-role     = "worker"
      workload-type = each.value.workload
    }
  )

  boot_disk {
    initialize_params {
      image = "ubuntu-os-cloud/ubuntu-2204-lts"
      size  = var.worker_disk_size
      type  = "pd-ssd"
    }
  }

  network_interface {
    subnetwork = var.subnet_self_link

    access_config {
      nat_ip = google_compute_address.worker_external_ip[each.key].address
    }
  }

  metadata = merge(
    local.common_metadata,
    {
      user-data = templatefile("${path.module}/templates/cloud-init-worker.yaml", {
        hostname = each.value.name
        workload = each.value.workload
      })
    }
  )

  service_account {
    email  = var.service_account_email
    scopes = ["cloud-platform"]
  }

  allow_stopping_for_update = true

  lifecycle {
    ignore_changes = [metadata["ssh-keys"]]
  }
}

# -----------------------------------------------------------------------------
# Instance Groups for Load Balancer
# -----------------------------------------------------------------------------

resource "google_compute_instance_group" "workers" {
  name      = "orange-wallet-workers"
  zone      = var.zone
  project   = var.project_id
  instances = [for worker in google_compute_instance.worker : worker.self_link]

  named_port {
    name = "http"
    port = 80
  }

  named_port {
    name = "https"
    port = 443
  }
}
