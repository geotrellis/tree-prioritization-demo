#
# ECS Resources
#

# Logs destination for ECS service
resource "aws_cloudwatch_log_group" "tree_prioritization" {
  name              = "log${var.environment}TreePrioritization"
  retention_in_days = "${var.cloudwatch_log_retention_days}"

  tags {
    Environment = "${var.environment}"
  }
}

# Template for container definition, allows us to inject environment
data "template_file" "ecs_tree_prioritization_task" {
  template = "${file("${path.module}/task-definitions/tree-prioritization.json")}"

  vars {
    tp_api_server_image = "${var.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/gt-tree-prioritization:${var.image_version}"
    tp_nginx_image      = "${var.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/gt-tree-prioritization-nginx:${var.image_version}"
    tp_environment      = "${var.environment}"
    tp_region           = "${var.aws_region}"
  }
}

# Allows resource sharing among multiple containers
resource "aws_ecs_task_definition" "tree_prioritization" {
  family                = "${var.environment}TreePrioritization"
  container_definitions = "${data.template_file.ecs_tree_prioritization_task.rendered}"
}

data "aws_iam_role" "autoscaling" {
  role_name = "${var.ecs_autoscaling_role_name}"
}

module "tree_prioritization_ecs_service" {
  source = "github.com/azavea/terraform-aws-ecs-web-service?ref=0.2.0"

  name                = "TreePrioritization"
  vpc_id              = "${data.terraform_remote_state.core.vpc_id}"
  public_subnet_ids   = ["${data.terraform_remote_state.core.public_subnet_ids}"]
  access_log_bucket   = "${data.terraform_remote_state.core.logs_bucket_id}"
  access_log_prefix   = "ALB/TreePrioritization"
  port                = "443"
  ssl_certificate_arn = "${var.ssl_certificate_arn}"

  cluster_name                   = "${data.terraform_remote_state.core.container_instance_name}"
  task_definition_id             = "${aws_ecs_task_definition.tree_prioritization.family}:${aws_ecs_task_definition.tree_prioritization.revision}"
  desired_count                  = "${var.tree_prioritization_ecs_desired_count}"
  min_count                      = "${var.tree_prioritization_ecs_min_count}"
  max_count                      = "${var.tree_prioritization_ecs_max_count}"
  deployment_min_healthy_percent = "${var.tree_prioritization_ecs_deployment_min_percent}"
  deployment_max_percent         = "${var.tree_prioritization_ecs_deployment_max_percent}"
  container_name                 = "nginx"
  container_port                 = "443"
  health_check_path              = "/tile/gt/health-check/"
  ecs_service_role_name          = "${data.terraform_remote_state.core.ecs_service_role_name}"
  ecs_autoscale_role_arn         = "${data.aws_iam_role.autoscaling.arn}"

  project     = "Geotrellis Tree Prioritization"
  environment = "${var.environment}"
}
