# Terraform Outputs
# These values will be displayed after terraform apply and can be used by CI/CD pipelines

output "master_external_ip" {
  description = "External IP address of K3s master node"
  value       = module.compute.master_external_ip
}

output "master_internal_ip" {
  description = "Internal IP address of K3s master node"
  value       = module.compute.master_internal_ip
}

output "worker_external_ip" {
  description = "External IP address of K3s worker node"
  value       = module.compute.worker_external_ip
}

output "worker_internal_ip" {
  description = "Internal IP address of K3s worker node"
  value       = module.compute.worker_internal_ip
}

output "vpc_network_name" {
  description = "Name of the VPC network"
  value       = module.network.vpc_network_name
}

output "subnet_name" {
  description = "Name of the subnet"
  value       = module.network.subnet_name
}

output "metallb_ip_range" {
  description = "IP address range allocated for MetalLB load balancer"
  value       = "${module.network.metallb_ip_range_start}-${module.network.metallb_ip_range_end}"
}

output "ssh_command_master" {
  description = "SSH command to connect to master node"
  value       = "ssh ${var.ssh_user}@${module.compute.master_external_ip}"
}

output "ssh_command_worker" {
  description = "SSH command to connect to worker node"
  value       = "ssh ${var.ssh_user}@${module.compute.worker_external_ip}"
}

output "kubeconfig_command" {
  description = "Command to retrieve kubeconfig from master node"
  value       = "scp ${var.ssh_user}@${module.compute.master_external_ip}:/etc/rancher/k3s/k3s.yaml ~/.kube/config"
}

output "k3s_token_command" {
  description = "Command to retrieve K3s token from master node (needed for worker join)"
  value       = "ssh ${var.ssh_user}@${module.compute.master_external_ip} 'sudo cat /var/lib/rancher/k3s/server/node-token'"
}

output "service_account_email" {
  description = "Email of the GCP service account created for K3s nodes"
  value       = module.iam.service_account_email
}

# Formatted output for GitHub Actions secrets
output "github_actions_setup" {
  description = "Information needed for GitHub Actions secrets"
  value = {
    master_ip = module.compute.master_external_ip
    worker_ip = module.compute.worker_external_ip
    ssh_user  = var.ssh_user
  }
  sensitive = false
}

# Quick reference summary
output "deployment_summary" {
  description = "Summary of deployed infrastructure"
  value = <<-EOT

    ========================================
    Orange Wallet K3s Infrastructure
    ========================================

    Environment: ${var.environment}
    Region: ${var.region}
    Zone: ${var.zone}

    Master Node:
      - External IP: ${module.compute.master_external_ip}
      - Internal IP: ${module.compute.master_internal_ip}
      - Machine Type: ${var.master_machine_type}
      - SSH: ssh ${var.ssh_user}@${module.compute.master_external_ip}

    Worker Node:
      - External IP: ${module.compute.worker_external_ip}
      - Internal IP: ${module.compute.worker_internal_ip}
      - Machine Type: ${var.worker_machine_type}
      - SSH: ssh ${var.ssh_user}@${module.compute.worker_external_ip}

    Network:
      - VPC: ${module.network.vpc_network_name}
      - Subnet: ${module.network.subnet_name}
      - MetalLB IP Range: ${module.network.metallb_ip_range_start}-${module.network.metallb_ip_range_end}

    Next Steps:
      1. SSH to master: ssh ${var.ssh_user}@${module.compute.master_external_ip}
      2. Install K3s: cd /home/${var.ssh_user}/k3s-setup && sudo ./install-k3s-master.sh
      3. SSH to worker: ssh ${var.ssh_user}@${module.compute.worker_external_ip}
      4. Join worker: cd /home/${var.ssh_user}/k3s-setup && sudo ./install-k3s-worker.sh
      5. Get kubeconfig: scp ${var.ssh_user}@${module.compute.master_external_ip}:/etc/rancher/k3s/k3s.yaml ~/.kube/config

    ========================================
  EOT
}
