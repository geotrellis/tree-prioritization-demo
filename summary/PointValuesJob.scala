package org.opentreemap.modeling.summary

import org.opentreemap.modeling.S3CatalogReading

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark._
import spark.jobserver._
import geotrellis.spark._
import geotrellis.vector._

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
    val tileReader = catalog(sc).tileReader[SpatialKey](params.layerId)
    val rasterMetaData = catalog(sc).getLayerMetadata(params.layerId).rasterMetaData
    val values = getValuesAtPoints(tileReader, rasterMetaData)(params.pointsWithIds)
    val keyedValues = values map {
      case (id, point, value) => id -> value
    }
    val elapsedTime = System.currentTimeMillis - startTime
    Map(
      "elapsed" -> elapsedTime,
      "values" -> keyedValues.toMap
    )
  }
}
