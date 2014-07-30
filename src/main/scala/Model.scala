package org.opentreemap.modeling

import geotrellis.RasterExtent
import geotrellis.TypeByte
import geotrellis.source.RasterSource

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

