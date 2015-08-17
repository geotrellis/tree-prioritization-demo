package org.opentreemap.modeling.summary

import org.opentreemap.modeling.VectorHandling

import geotrellis.spark._
import geotrellis.vector._
import com.typesafe.config.{Config, ConfigFactory}

object PointValuesJobConfig extends VectorHandling {
  def apply(config: Config) = {
    val zoom = config.getInt("input.zoom")
    val layerId = LayerId(config.getString("input.layer"), zoom)
    val srid = config.getInt("input.srid")
    val pointsWithIds = config.getString("input.coords").split(",").grouped(3).map {
      case Array(id, xParam, yParam) =>
        try {
          val pt = reprojectPoint(
            Point(xParam.toDouble, yParam.toDouble),
            srid
          )
          Some((id, pt))
        } catch {
          case ex: NumberFormatException => None
        }
    }.toList.flatten
    new PointValuesJobConfig(layerId, pointsWithIds)
  }
}

class PointValuesJobConfig(val layerId: LayerId, val pointsWithIds: Seq[(String, Point)])
