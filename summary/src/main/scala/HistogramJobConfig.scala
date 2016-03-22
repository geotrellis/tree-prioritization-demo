package org.opentreemap.modeling

import geotrellis.spark._
import geotrellis.vector._
import com.typesafe.config.{Config, ConfigFactory}

object HistogramJobConfig extends VectorHandling {
  def apply(config: Config) = {
    val polyMaskParam = config.getString("input.polyMask")
    val zoom = config.getInt("input.zoom")
    val layerId = LayerId(config.getString("input.layer"), zoom)
    val srid = config.getInt("input.srid")
    val polys = reprojectPolygons(
      parsePolyMaskParam(polyMaskParam),
      srid
    )
    new HistogramJobConfig(layerId, polys)
  }
}

class HistogramJobConfig(val layerId: LayerId, val areas: Seq[Polygon])
