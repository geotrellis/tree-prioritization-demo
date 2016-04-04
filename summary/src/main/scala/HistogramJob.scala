package org.opentreemap.modeling

import com.typesafe.config.Config
import org.apache.spark._

import spark.jobserver._
import geotrellis.vector._

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
    val resultList = result.values.map(v => Vector(v, result.itemCount(v)))
    val elapsedTime = System.currentTimeMillis - startTime
    Map(
      "elapsed" -> elapsedTime,
      "envelope" -> params.areas.envelope,
      "histogram" -> resultList
    )
  }
}
