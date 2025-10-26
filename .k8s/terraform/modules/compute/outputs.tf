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

output "master_internal_ip" {
  description = "Internal IP of master node"
  value       = google_compute_instance.master.network_interface[0].network_ip
}

output "worker_instance_id" {
  description = "ID of the worker instance"
  value       = google_compute_instance.worker.instance_id
}

output "worker_instance_name" {
  description = "Name of the worker instance"
  value       = google_compute_instance.worker.name
}

output "worker_external_ip" {
  description = "External IP of worker node"
  value       = try(google_compute_instance.worker.network_interface[0].access_config[0].nat_ip, "")
}

output "worker_internal_ip" {
  description = "Internal IP of worker node"
  value       = google_compute_instance.worker.network_interface[0].network_ip
}
