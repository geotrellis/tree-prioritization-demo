package org.opentreemap.modeling

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.spark._
import geotrellis.spark.op.local._
import geotrellis.spark.op.local.spatial._

trait LayerMasking {

  /** Combine multiple polygons into a single mask raster. */
  def polyMask(polyMasks: Iterable[Polygon])(model: RasterRDD[SpatialKey]): RasterRDD[SpatialKey] = {
    if (polyMasks.size > 0) {
      model.mask(polyMasks)
    } else {
      model
    }
  }

  /** Combine multiple rasters into a single raster.
    * The resulting raster will contain values from `model` only at
    * points that have values in every `layerMask`. (AND-like operation)
    */
  def layerMask(layerMasks: Iterable[RasterRDD[SpatialKey]])(model: RasterRDD[SpatialKey]): RasterRDD[SpatialKey] = {
    if (layerMasks.size > 0) {
      layerMasks.foldLeft(model) { (rdd, mask) =>
        rdd.combineTiles(mask) { (rddTile, maskTile) =>
          rddTile.combine(maskTile) { (z, maskValue) =>
            if (isData(maskValue)) z
            else NODATA
          }
        }
      }
    } else {
      model
    }
  }

  /** Filter all values from `model` that are less than `threshold`. */
  def thresholdMask(threshold: Int)(model: RasterRDD[SpatialKey]): RasterRDD[SpatialKey] = {
    if (threshold > NODATA) {
      model.localMap { z =>
        if (z >= threshold) z
        else NODATA
      }
    } else {
      model
    }
  }

 /** Filter model by 1 or more masks. */
  def applyMasks(model: RasterRDD[SpatialKey], masks: (RasterRDD[SpatialKey]) => RasterRDD[SpatialKey]*) = {
    masks.foldLeft(model) { (rdd, mask) =>
      mask(rdd)
    }
  }

}
