package org.opentreemap.modeling

import geotrellis.raster.histogram._
import geotrellis.spark._
import geotrellis.spark.mapalgebra.zonal._
import geotrellis.spark.summary.polygonal._
import geotrellis.vector._

trait HistogramLogic {
  def histogram(rdd: TileLayerRDD[SpatialKey], polyMask: Seq[Polygon]): Histogram[Int] = {
    if (polyMask.size > 0) {
      val multipolygon = MultiPolygon(polyMask)
      rdd.polygonalHistogram(multipolygon)
    } else {
      null
    }
  }
}
