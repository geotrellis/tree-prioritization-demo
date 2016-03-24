package org.opentreemap.modeling

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.spark._

trait TileLayerMasking {

  def polyTileMask(polyMasks: Iterable[Polygon])(tile: Tile): Tile = {
    // TODO: Pull in an updated Geotrellis when this is complete and merged
    // https://github.com/zifeo/geotrellis/commit/d63608cb7d77c0358a5dd8118f6289d6d9366799
    tile
  }

  def layerTileMask(layerMasks: Iterable[Tile])(tile: Tile): Tile = {
    if (layerMasks.size > 0) {
      layerMasks.foldLeft(tile) { (acc, mask) =>
        //acc.combine(mask) { (z, maskValue) =>
        // TODO: restore above line after switching to GeoTrellis 0.10
        acc.combine(mask.convert(TypeInt).toArrayTile) { (z, maskValue) =>
          if (isData(maskValue)) z
          else NODATA
        }
      }
    } else {
      tile
    }
  }

  def thresholdTileMask(threshold: Int)(tile: Tile): Tile = {
    if (threshold > NODATA) {
      tile.map { z =>
        if (z >= threshold) z
        else NODATA
      }
    } else {
      tile
    }
  }

  def applyTileMasks(tile: Tile, masks: (Tile) => Tile*) = {
    masks.foldLeft(tile) { (acc, mask) =>
      mask(acc)
    }
  }

}
