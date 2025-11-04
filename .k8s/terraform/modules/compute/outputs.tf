# Compute Module Outputs

output "master_instance_id" {
  description = "ID of the master instance"
  value       = google_compute_instance.master.instance_id
}

output "master_instance_name" {
  description = "Name of the master instance"
  value       = google_compute_instance.master.name
}

output "master_external_ip" {
  description = "External IP of master node"
  value       = try(google_compute_instance.master.network_interface[0].access_config[0].nat_ip, "")
}

output "master_static_ip_address" {
  description = "Reserved static IP address for master node (if enabled)"
  value       = var.use_static_ip_for_master ? google_compute_address.master_static_ip[0].address : null
}

output "master_internal_ip" {
  description = "Internal IP of master node"
  value       = google_compute_instance.master.network_interface[0].network_ip
}

output "worker_instance_ids" {
  description = "IDs of all worker instances"
  value       = google_compute_instance.worker[*].instance_id
}

output "worker_instance_names" {
  description = "Names of all worker instances"
  value       = google_compute_instance.worker[*].name
}

output "worker_external_ips" {
  description = "External IPs of all worker nodes"
  value       = [for worker in google_compute_instance.worker : try(worker.network_interface[0].access_config[0].nat_ip, "")]
}

output "worker_internal_ips" {
  description = "Internal IPs of all worker nodes"
  value       = google_compute_instance.worker[*].network_interface[0].network_ip
}

# Worker instance self links (as list for load balancer instance group)
output "worker_instance_self_links" {
  description = "List of all worker instance self links for load balancer"
  value       = google_compute_instance.worker[*].self_link
}

# Backward compatibility - returns first worker
output "worker_instance_id" {
  description = "[Deprecated] ID of the first worker instance (use worker_instance_ids)"
  value       = length(google_compute_instance.worker) > 0 ? google_compute_instance.worker[0].instance_id : ""
}

output "worker_instance_name" {
  description = "[Deprecated] Name of the first worker instance (use worker_instance_names)"
  value       = length(google_compute_instance.worker) > 0 ? google_compute_instance.worker[0].name : ""
}

output "worker_external_ip" {
  description = "[Deprecated] External IP of first worker node (use worker_external_ips)"
  value       = length(google_compute_instance.worker) > 0 ? try(google_compute_instance.worker[0].network_interface[0].access_config[0].nat_ip, "") : ""
}

output "worker_internal_ip" {
  description = "[Deprecated] Internal IP of first worker node (use worker_internal_ips)"
  value       = length(google_compute_instance.worker) > 0 ? google_compute_instance.worker[0].network_interface[0].network_ip : ""
}
