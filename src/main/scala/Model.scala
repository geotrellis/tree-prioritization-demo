package org.opentreemap.modeling

import geotrellis.raster._
import geotrellis.engine._
import geotrellis.engine.op.local._

object Model {
  def weightedOverlay(layers: Iterable[String],
                      weights: Iterable[Int],
                      rasterExtent: RasterExtent): RasterSource =
    layers
      .zip(weights)
      .map { case (layer, weight) =>
        RasterSource(layer, rasterExtent)
          .convert(TypeByte)
          .localMultiply(weight)
       }
      .localAdd
}

