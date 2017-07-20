import scala.util.Properties

object Version {
  def either(environmentVariable: String, default: String): String =
    Properties.envOrElse(environmentVariable, default)

  val modeling     = "1.5.1"
  val geotrellis   = "1.1.1"
  val scala        = "2.11.8"
  val scalaTest   = "3.0.1"
  val akkaActor   = "2.4.16"
  val akkaHttp    = "10.0.3"
  val ficus       = "1.4.0"
  lazy val hadoop  = either("SPARK_HADOOP_VERSION", "2.6.0")
  lazy val spark   = either("SPARK_VERSION", "2.1.0")
}
