variable "environment" {
  default = "Production"
}

variable "remote_state_bucket" {
  type        = "string"
  description = "Core infrastructure config bucket"
}

variable "aws_account_id" {
  default     = "896538046175"
  description = "Geotrellis account ID"
}

variable "aws_region" {
  default = "us-east-1"
}

variable "image_version" {
  type        = "string"
  description = "Tree Prioritization API and Nginx Image version"
}

variable "cdn_price_class" {
  default = "PriceClass_200"
}

variable "ssl_certificate_arn" {}

variable "cloudwatch_log_retention_days" {
  default = "30"
}

variable "ecs_autoscaling_role_name" {
  default = "AWSServiceRoleForApplicationAutoScaling_ECSService"
}

variable "tree_prioritization_ecs_desired_count" {
  default = "1"
}

variable "tree_prioritization_ecs_min_count" {
  default = "1"
}

variable "tree_prioritization_ecs_max_count" {
  default = "2"
}

variable "tree_prioritization_ecs_deployment_min_percent" {
  default = "100"
}

variable "tree_prioritization_ecs_deployment_max_percent" {
  default = "200"
}
