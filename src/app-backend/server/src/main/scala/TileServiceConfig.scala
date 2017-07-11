package org.opentreemap.modeling

import com.typesafe.config.ConfigFactory
import geotrellis.spark.io.s3.S3InputFormat
import net.ceedubs.ficus.Ficus
import net.ceedubs.ficus.readers.ArbitraryTypeReader

object TileServiceConfig {
  import ArbitraryTypeReader._
  import Ficus._

  private val config = ConfigFactory.load()

  val configHost = config.as[String]("http.interface")
  val configPort = config.as[Int]("http.port")
  val rollbarAccessToken = System.getenv("ROLLBAR_SERVER_SIDE_ACCESS_TOKEN")
  val otmStackType = System.getenv("OTM_STACK_TYPE")

  val catalogPath = config.as[String]("server.catalog")

  lazy val isS3Catalog = try {
    val S3InputFormat.S3UrlRx(_, _, _, _) = catalogPath
    true
  } catch { case _: Exception => false }

  lazy val S3CatalogPath = {
    val S3InputFormat.S3UrlRx(_, _, bucket, prefix) = catalogPath
    bucket -> prefix
  }
}
