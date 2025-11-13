# Network Module Outputs

output "vpc_network_id" {
  description = "ID of the VPC network"
  value       = google_compute_network.vpc.id
}

output "vpc_network_name" {
  description = "Name of the VPC network"
  value       = google_compute_network.vpc.name
}

output "vpc_network_self_link" {
  description = "Self link of the VPC network"
  value       = google_compute_network.vpc.self_link
}

output "subnet_id" {
  description = "ID of the subnet"
  value       = google_compute_subnetwork.subnet.id
}

output "subnet_name" {
  description = "Name of the subnet"
  value       = google_compute_subnetwork.subnet.name
}

output "subnet_self_link" {
  description = "Self link of the subnet"
  value       = google_compute_subnetwork.subnet.self_link
}

output "subnet_cidr" {
  description = "CIDR range of the subnet"
  value       = google_compute_subnetwork.subnet.ip_cidr_range
}

output "metallb_ip_range_start" {
  description = "MetalLB IP range start"
  value       = "${split(".", var.subnet_cidr)[0]}.${split(".", var.subnet_cidr)[1]}.${split(".", var.subnet_cidr)[2]}.${var.metallb_ip_range_start}"
}

output "metallb_ip_range_end" {
  description = "MetalLB IP range end"
  value       = "${split(".", var.subnet_cidr)[0]}.${split(".", var.subnet_cidr)[1]}.${split(".", var.subnet_cidr)[2]}.${var.metallb_ip_range_end}"
}

output "router_name" {
  description = "Name of the Cloud Router"
  value       = google_compute_router.router.name
}

output "nat_name" {
  description = "Name of the Cloud NAT"
  value       = google_compute_router_nat.nat.name
}
