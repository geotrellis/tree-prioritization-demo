package org.opentreemap.modeling

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark._
import org.apache.spark.SparkContext._

import scala.collection.mutable
import scala.util.Try

import spark.jobserver._

import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.JsonParser.ParsingException

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.rasterize.{Rasterizer, Callback}
import geotrellis.spark._
import geotrellis.spark.op.zonal.summary._
import geotrellis.vector._
import geotrellis.vector.io.json._
import geotrellis.vector.reproject._

object HistogramJob
    extends SparkJob
    with HistogramLogic
    with VectorHandling
    with S3CatalogReading {

  override def validate(sc: SparkContext, config: Config): SparkJobValidation = {
    SparkJobValid
    // TODO: Validate the `config` object
  }

  override def runJob(sc: SparkContext, config: Config): Any = {
    val startTime = System.currentTimeMillis
    val params = HistogramJobConfig(config)
    val layer = queryAndCropLayer(sc, params.layerId, params.areas.envelope)
    val result = histogram(layer, params.areas)
    val resultList = result.getValues.map(v => Vector(v, result.getItemCount(v)))
    val elapsedTime = System.currentTimeMillis - startTime
    Map(
      "elapsed" -> elapsedTime,
      "envelope" -> params.areas.envelope,
      "histogram" -> resultList
    )
  }
}