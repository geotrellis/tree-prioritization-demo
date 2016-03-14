package org.opentreemap.modeling

import geotrellis.raster.histogram._
import geotrellis.spark._
import geotrellis.spark.op.zonal.summary._
import geotrellis.vector._

trait HistogramLogic {
  def histogram(rdd: RasterRDD[SpatialKey], polyMask: Seq[Polygon]): Histogram = {
    if (polyMask.size > 0) {
      val histograms: Seq[Histogram] = polyMask map {
        p => {
          rdd.zonalHistogram(p)
        }
      }
      FastMapHistogram.fromHistograms(histograms)
    } else {
      null
    }
  }
}
