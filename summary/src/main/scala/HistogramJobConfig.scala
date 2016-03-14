package org.opentreemap.modeling

import geotrellis.spark._
import geotrellis.vector._
import com.typesafe.config.{Config, ConfigFactory}

object HistogramJobConfig extends VectorHandling {
  def apply(config: Config) = {
    val polyMaskParam = config.getString("input.polyMask")
    val zoom = config.getInt("input.zoom")
    val layerId = LayerId(config.getString("input.layer"), zoom)
    // TOOD: Read CRS from config
    new HistogramJobConfig(layerId, parsePolyMaskParam(polyMaskParam))
  }
}

class HistogramJobConfig(val layerId: LayerId, val areas: Seq[Polygon])
