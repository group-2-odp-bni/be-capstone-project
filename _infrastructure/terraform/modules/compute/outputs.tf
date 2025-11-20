# =============================================================================
# Compute Module - Outputs
# =============================================================================

# -----------------------------------------------------------------------------
# Master Node Outputs
# -----------------------------------------------------------------------------

output "master_node_name" {
  description = "Name of the master node"
  value       = google_compute_instance.master.name
}

output "master_node_id" {
  description = "ID of the master node"
  value       = google_compute_instance.master.id
}

output "master_internal_ip" {
  description = "Internal IP address of the master node"
  value       = google_compute_instance.master.network_interface[0].network_ip
}

output "master_external_ip" {
  description = "External IP address of the master node"
  value       = google_compute_address.master_external_ip.address
}

# -----------------------------------------------------------------------------
# Worker Nodes Outputs
# -----------------------------------------------------------------------------

output "worker_nodes" {
  description = "Map of worker node details"
  value = {
    for key, worker in google_compute_instance.worker : key => {
      name        = worker.name
      id          = worker.id
      internal_ip = worker.network_interface[0].network_ip
      external_ip = google_compute_address.worker_external_ip[key].address
      machine_type = worker.machine_type
      workload    = worker.labels["workload-type"]
    }
  }
}

output "worker_internal_ips" {
  description = "List of internal IPs for all worker nodes"
  value       = [for worker in google_compute_instance.worker : worker.network_interface[0].network_ip]
}

output "worker_external_ips" {
  description = "List of external IPs for all worker nodes"
  value       = [for ip in google_compute_address.worker_external_ip : ip.address]
}

output "worker_instance_groups" {
  description = "Instance groups for load balancer backend (Deprecated, use worker_instances_self_links)"
  value = {
    workers = google_compute_instance_group.workers.self_link
  }
}

output "worker_instances_self_links" {
  description = "List of self_links for all worker compute instances"
  value       = [for worker in google_compute_instance.worker : worker.self_link]
}

output "worker_nodes_tag" {
  description = "The common network tag applied to all worker nodes"
  value       = "k3s-node"
}

# -----------------------------------------------------------------------------
# All Nodes Summary
# -----------------------------------------------------------------------------

output "all_nodes" {
  description = "Summary of all cluster nodes"
  value = merge(
    {
      master = {
        name        = google_compute_instance.master.name
        internal_ip = google_compute_instance.master.network_interface[0].network_ip
        external_ip = google_compute_address.master_external_ip.address
        role        = "master"
      }
    },
    {
      for key, worker in google_compute_instance.worker : key => {
        name        = worker.name
        internal_ip = worker.network_interface[0].network_ip
        external_ip = google_compute_address.worker_external_ip[key].address
        role        = "worker"
        workload    = worker.labels["workload-type"]
      }
    }
  )
}

# -----------------------------------------------------------------------------
# Ansible Inventory
# -----------------------------------------------------------------------------

output "ansible_inventory" {
  description = "Rendered Ansible inventory in YAML format"
  value = templatefile("${path.module}/templates/inventory.yml.tpl", {
    ssh_user = var.ssh_user

    master_name = google_compute_instance.master.name
    master_ip   = google_compute_address.master_external_ip.address

    stateless_workers = [
      for key, worker in google_compute_instance.worker :
      {
        name = worker.name
        ip   = google_compute_address.worker_external_ip[key].address
      }
      if worker.labels["workload-type"] == "stateless"
    ]

    stateful_workers = [
      for key, worker in google_compute_instance.worker :
      {
        name = worker.name
        ip   = google_compute_address.worker_external_ip[key].address
      }
      if worker.labels["workload-type"] == "stateful"
    ]
  })
}
