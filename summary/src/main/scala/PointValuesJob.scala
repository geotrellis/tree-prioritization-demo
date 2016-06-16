package org.opentreemap.modeling

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._

import com.typesafe.config.Config
import org.apache.spark._

import spark.jobserver._

object PointValuesJob
    extends SparkJob
    with S3CatalogReading
    with PointValuesLogic {

  override def validate(sc: SparkContext, config: Config): SparkJobValidation = {
    SparkJobValid
    // TODO: Validate the `config` object
  }

  override def runJob(sc: SparkContext, config: Config): Any = {
    val startTime = System.currentTimeMillis
    val params = PointValuesJobConfig(config)
    val reader = tileReaderNoProxy(sc).reader[SpatialKey, Tile](params.layerId)
    val values = getValuesAtPoints(reader, metadata(sc, params.layerId))(params.pointsWithIds)
    val coords = values map {
      case (id, point, value) => Vector(id, point.x, point.y, value)
    }
    val elapsedTime = System.currentTimeMillis - startTime
    Map(
      "elapsed" -> elapsedTime,
      "coords" -> coords
    )
  }
}
