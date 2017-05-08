package org.opentreemap.modeling

import scala.math.{Pi, pow}

import geotrellis.raster._
import geotrellis.raster.rasterize._
import geotrellis.vector._

trait TileLayerMasking {

  val tileSize:Int = 256
  val initialResolution:Double = 2 * Pi * 6378137 / tileSize
  val originShift:Double = 2 * Pi * 6378137 / 2.0

  def resolution(zoom:Int):Double = {
    initialResolution / pow(2, zoom)
  }

  def pixelsToMeters(px:Int, py:Int, zoom:Int):(Double, Double) = {
    val res = resolution(zoom)
    val mx = px * res - originShift
    val my = py * res - originShift
    return (mx, my)
  }

  def webMercatorTileExtent(tx:Int, ty:Int, zoom:Int):Extent = {
    // Convert ZXY to TMS (The Y value needs to be "flipped")
    val fy = pow(2, zoom).toInt - ty - 1
    val (minx, miny) = pixelsToMeters( tx*tileSize, fy*tileSize, zoom )
    val (maxx, maxy) = pixelsToMeters( (tx+1)*tileSize, (fy+1)*tileSize, zoom )
    Extent( minx, miny, maxx, maxy )
  }

  def polyTileMask(polyMasks: Iterable[Polygon], z:Int, x:Int, y:Int)(tile: Tile): Tile = {
    if (polyMasks.size > 0) {
      val (cols, rows) = tile.dimensions
      val re = RasterExtent(tile, webMercatorTileExtent(x, y, z))
      val result = ArrayTile.empty(tile.cellType, cols, rows)
      for(g <- polyMasks) {
        Rasterizer.foreachCellByGeometry(g, re) { (col: Int, row: Int) =>
          result.set(col, row, tile.get(col, row))
        }
      }
      result:Tile
    } else {
      tile
    }
  }

  def layerTileMask(layerMasks: Iterable[Tile])(tile: Tile): Tile = {
    if (layerMasks.size > 0) {
      layerMasks.foldLeft(tile) { (acc, mask) =>
        acc.combine(mask) { (z, maskValue) =>
          if (isData(maskValue)) z
          else NODATA
        }
      }
    } else {
      tile
    }
  }

  def thresholdTileMask(threshold: Int)(tile: Tile): Tile = {
    if (isData(threshold)) {
      tile.map { z =>
        if (z >= threshold) z
        else NODATA
      }
    } else {
      tile
    }
  }

  def applyTileMasks(tile: Tile, masks: (Tile => Tile)*) = {
    masks.foldLeft(tile) { (acc, mask) =>
      mask(acc)
    }
  }

}
