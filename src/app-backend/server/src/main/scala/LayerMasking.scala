package org.opentreemap.modeling

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.spark._

trait LayerMasking {

  /** Combine multiple polygons into a single mask raster. */
  def polyMask(polyMasks: Iterable[Polygon])(model: TileLayerCollection[SpatialKey]): TileLayerCollection[SpatialKey] = {
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

  def layerMask(layerMasks: Iterable[TileLayerCollection[SpatialKey]])(model: TileLayerCollection[SpatialKey]): TileLayerCollection[SpatialKey] = {
    if (layerMasks.size > 0) {
      layerMasks.foldLeft(model) { (layer, mask) =>
        layer.withContext { seq =>
          seq.combineValues(mask) {
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
  def thresholdMask(threshold: Int)(model: TileLayerCollection[SpatialKey]): TileLayerCollection[SpatialKey] = {
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
  def applyMasks(model: TileLayerCollection[SpatialKey],
                 masks: (TileLayerCollection[SpatialKey] => TileLayerCollection[SpatialKey])*
                ) = {
    masks.foldLeft(model) { (layer, mask) =>
      mask(layer)
    }
  }

}
