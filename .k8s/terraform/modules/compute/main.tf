# Compute Module - GCP VM Instances for K3s Master and Worker Nodes

# Data source for latest Ubuntu 22.04 LTS image
data "google_compute_image" "ubuntu" {
  family  = "ubuntu-2204-lts"
  project = "ubuntu-os-cloud"
}

# Read SSH public key
data "local_file" "ssh_public_key" {
  filename = pathexpand(var.ssh_public_key_path)
}

# Template file for K3s master installation script
locals {
  master_startup_script = templatefile("${path.module}/scripts/master-startup.sh", {
    k3s_version            = var.k3s_version
    k3s_token              = var.k3s_token
    ssh_user               = var.ssh_user
    metallb_ip_range_start = var.metallb_ip_range_start
    metallb_ip_range_end   = var.metallb_ip_range_end
    subnet_cidr            = var.subnet_cidr
  })

  worker_startup_script = templatefile("${path.module}/scripts/worker-startup.sh", {
    k3s_version  = var.k3s_version
    k3s_token    = var.k3s_token
    master_ip    = google_compute_instance.master.network_interface[0].network_ip
    ssh_user     = var.ssh_user
  })
}

# K3s Master Node
resource "google_compute_instance" "master" {
  name         = "${var.project_name}-${var.environment}-master"
  machine_type = var.master_machine_type
  zone         = var.zone
  project      = var.project_id

  tags = ["k3s-node", "k3s-master"]

  boot_disk {
    initialize_params {
      image = data.google_compute_image.ubuntu.self_link
      size  = var.master_disk_size
      type  = var.master_disk_type
    }
  }

  network_interface {
    network    = var.vpc_network_name
    subnetwork = var.subnet_id

    dynamic "access_config" {
      for_each = var.enable_external_ip ? [1] : []
      content {
        // Ephemeral public IP
      }
    }
  }

  metadata = {
    ssh-keys               = "${var.ssh_user}:${trimspace(data.local_file.ssh_public_key.content)}"
    enable-oslogin         = "FALSE"
    block-project-ssh-keys = "FALSE"
  }

  metadata_startup_script = local.master_startup_script

  service_account {
    email  = var.service_account_email
    scopes = [
      "https://www.googleapis.com/auth/cloud-platform",
      "https://www.googleapis.com/auth/compute",
      "https://www.googleapis.com/auth/devstorage.read_only",
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring.write"
    ]
  }

  scheduling {
    preemptible       = var.enable_preemptible_vms
    automatic_restart = !var.enable_preemptible_vms
  }

  labels = merge(
    var.labels,
    {
      node-role = "master"
    }
  )

  allow_stopping_for_update = true

  lifecycle {
    ignore_changes = [
      metadata_startup_script, # Prevent rerun on every apply
    ]
  }
}

# K3s Worker Node
resource "google_compute_instance" "worker" {
  name         = "${var.project_name}-${var.environment}-worker"
  machine_type = var.worker_machine_type
  zone         = var.zone
  project      = var.project_id

  tags = ["k3s-node", "k3s-worker"]

  boot_disk {
    initialize_params {
      image = data.google_compute_image.ubuntu.self_link
      size  = var.worker_disk_size
      type  = var.worker_disk_type
    }
  }

  network_interface {
    network    = var.vpc_network_name
    subnetwork = var.subnet_id

    dynamic "access_config" {
      for_each = var.enable_external_ip ? [1] : []
      content {
        // Ephemeral public IP
      }
    }
  }

  metadata = {
    ssh-keys               = "${var.ssh_user}:${trimspace(data.local_file.ssh_public_key.content)}"
    enable-oslogin         = "FALSE"
    block-project-ssh-keys = "FALSE"
  }

  metadata_startup_script = local.worker_startup_script

  service_account {
    email  = var.service_account_email
    scopes = [
      "https://www.googleapis.com/auth/cloud-platform",
      "https://www.googleapis.com/auth/compute",
      "https://www.googleapis.com/auth/devstorage.read_only",
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring.write"
    ]
  }

  scheduling {
    preemptible       = var.enable_preemptible_vms
    automatic_restart = !var.enable_preemptible_vms
  }

  labels = merge(
    var.labels,
    {
      node-role = "worker"
    }
  )

  allow_stopping_for_update = true

  # Worker depends on master to get master IP
  depends_on = [google_compute_instance.master]

  lifecycle {
    ignore_changes = [
      metadata_startup_script,
    ]
  }
}
