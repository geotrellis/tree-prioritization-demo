package org.opentreemap.modeling.tile

import com.typesafe.config.ConfigFactory

object TileServiceConfig {
  private val config = ConfigFactory.load()

  private def intFromEnvOrConfig(envVar: String, configKey: String): Int = {
    Option(System.getenv(envVar)) match {
      case Some(x) => try {
          x.toInt
        } catch {
          case _ : Throwable => config.getInt(configKey)
        }
      case None => config.getInt(configKey)
    }
  }

  private def stringFromEnvOrConfig(envVar: String, configKey: String): String = {
    Option(System.getenv(envVar)).getOrElse(config.getString(configKey))
  }

  val host = stringFromEnvOrConfig("HOST", "geotrellis.host")
  val port = intFromEnvOrConfig("PORT", "geotrellis.port")
  val staticPath = stringFromEnvOrConfig("STATIC_PATH", "server.static-path")
  val featuresPath = stringFromEnvOrConfig("FEATURES_PATH", "server.features-path")
}
