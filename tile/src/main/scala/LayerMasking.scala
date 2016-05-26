package org.opentreemap.modeling

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.spark._

trait LayerMasking {

  /** Combine multiple polygons into a single mask raster. */
  def polyMask(polyMasks: Iterable[Polygon])(model: TileLayerRDD[SpatialKey]): TileLayerRDD[SpatialKey] = {
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

  def layerMask(layerMasks: Iterable[TileLayerRDD[SpatialKey]])(model: TileLayerRDD[SpatialKey]): TileLayerRDD[SpatialKey] = {
    if (layerMasks.size > 0) {
      layerMasks.foldLeft(model) { (rdd, mask) =>
        rdd.withContext {
          _.join(mask).combineValues {
            _.combine(_) { (z, maskValue) =>
              if (isData(maskValue)) z
              else NODATA
            }
          }
        }
      }
    } else {
      model
    }
  }

  /** Filter all values from `model` that are less than `threshold`. */
  def thresholdMask(threshold: Int)(model: TileLayerRDD[SpatialKey]): TileLayerRDD[SpatialKey] = {
    if (isData(threshold)) {
      model.withContext {
        _.localMap { z =>
          if (z >= threshold) z
          else NODATA
        }
      }
    } else {
      model
    }
  }

 /** Filter model by 1 or more masks. */
  def applyMasks(model: TileLayerRDD[SpatialKey],
                 masks: (TileLayerRDD[SpatialKey] => TileLayerRDD[SpatialKey])*
                ) = {
    masks.foldLeft(model) { (rdd, mask) =>
      mask(rdd)
    }
  }

}
