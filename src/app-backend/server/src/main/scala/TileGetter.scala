package org.opentreemap.modeling

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io.Intersects
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.tiling._
import geotrellis.vector._

trait TileGetter { self: ReaderSet =>
  import ModelingTypes._

  val breaksZoom = 8

  private def getMetadata(layerId: LayerId): TileLayerMetadata[SpatialKey] =
    attributeStore
      .readMetadata[TileLayerMetadata[SpatialKey]](layerId)

  private def getMaxZoom(layerName: String): Int =
    attributeStore.layerIds.groupBy(_.name)(layerName).map(_.zoom).max

  def getTileWithZoom(layer:String,
                      z:Int,
                      x:Int,
                      y:Int): Tile = {
    val maxZoom = getMaxZoom(layer)
    if (z <= maxZoom) {
      val reader = tileReader.reader[SpatialKey, Tile](layer, z)
      reader(SpatialKey(x, y))
    } else {
      val layerId = LayerId(layer, maxZoom)
      val reader = tileReader.reader[SpatialKey, Tile](layerId)
      val rmd = getMetadata(layerId)
      val layoutLevel = ZoomedLayoutScheme(rmd.crs).levelForZoom(rmd.extent, z)
      val mapTransform = MapKeyTransform(rmd.crs, layoutLevel.layout.layoutCols, layoutLevel.layout.layoutRows)
      val targetExtent = mapTransform(x, y)
      val gb @ GridBounds(nx, ny, _, _) = rmd.mapTransform(targetExtent)
      val sourceExtent = rmd.mapTransform(nx, ny)
      val largerTile = reader(SpatialKey(nx, ny))
      largerTile.resample(sourceExtent, RasterExtent(targetExtent, 512, 512))
    }
  }

  /** Convert `layerMask` map to list of filtered rasters.
    * The result contains a raster for each layer specified,
    * and that raster only contains whitelisted values present
    * in the `layerMask` argument.
    */
  def getMasksFromCatalog(layerMask: Option[LayerMaskType],
                          extent: Extent,
                          zoom: Int): Iterable[TileLayerCollection[SpatialKey]] = {
    layerMask match {
      case Some(masks: LayerMaskType) =>
        masks map { case (layerName: String, values: Array[Int]) =>
          collectionReader.query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]]((layerName, zoom))
            .where(Intersects(extent))
            .result.withContext {
              _.localMap { z =>
                if (values contains z) z
                else NODATA
              }
            }
          }
      case None =>
        Seq[TileLayerCollection[SpatialKey]]()
    }
  }

  def getMaskTiles(layerMask: Option[LayerMaskType],
                   z:Int, x:Int, y:Int): Iterable[Tile] = {
    layerMask match {
      case Some(masks: LayerMaskType) =>
        masks map { case (layer, values) =>
          val tile = getTileWithZoom(layer, z, x, y)
          tile.map { z =>
            if (values contains z) z
            else NODATA
          }
        }
      case None =>
        Seq[Tile]()
    }
  }

}
