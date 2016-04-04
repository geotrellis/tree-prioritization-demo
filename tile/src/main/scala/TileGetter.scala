package org.opentreemap.modeling

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io.Intersects
import geotrellis.spark.io._
import geotrellis.spark.io.s3.{S3LayerReader, S3TileReader}
import geotrellis.spark.tiling._
import geotrellis.vector._

import org.apache.spark._

object TileGetter {
  import ModelingTypes._

  def _getMetadata(implicit sc: SparkContext, tileReader: S3TileReader[SpatialKey, Tile], layerId: LayerId): TileLayerMetadata[SpatialKey] =
    tileReader
      .attributeStore
      .readMetadata[TileLayerMetadata[SpatialKey]](layerId)

  def getTileWithZoom(implicit sc: SparkContext,
                      tileReader: S3TileReader[SpatialKey, Tile],
                      layer:String,
                      z:Int,
                      x:Int,
                      y:Int,
                      maxZoom:Int): Tile = {
    if (z <= maxZoom) {
      val reader = tileReader.read(layer, z)
      reader(SpatialKey(x, y))
    } else {
      val layerId = LayerId(layer, maxZoom)
      val reader = tileReader.read(layerId)
      val rmd = _getMetadata(sc, tileReader, layerId)
      val layoutLevel = ZoomedLayoutScheme(rmd.crs).levelForZoom(rmd.extent, z)
      val mapTransform = MapKeyTransform(rmd.crs, layoutLevel.layout.layoutCols, layoutLevel.layout.layoutRows)
      val targetExtent = mapTransform(x, y)
      val gb @ GridBounds(nx, ny, _, _) = rmd.mapTransform(targetExtent)
      val sourceExtent = rmd.mapTransform(nx, ny)
      val largerTile = reader(SpatialKey(nx, ny))
      largerTile.resample(sourceExtent, RasterExtent(targetExtent, 256, 256))
    }
  }

  /** Convert `layerMask` map to list of filtered rasters.
    * The result contains a raster for each layer specified,
    * and that raster only contains whitelisted values present
    * in the `layerMask` argument.
    */
  def getMasksFromCatalog(implicit sc:SparkContext,
                          catalog: S3LayerReader,
                          layerMask: Option[LayerMaskType],
                          extent: Extent,
    zoom: Int): Iterable[TileLayerRDD[SpatialKey]] = {
    layerMask match {
      case Some(masks: LayerMaskType) =>
        masks map { case (layerName: String, values: Array[Int]) =>
          catalog.query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]]((layerName, zoom))
            .where(Intersects(extent))
            .result.withContext {
              _.localMap { z =>
                if (values contains z) z
                else NODATA
              }
            }
          }
      case None =>
        Seq[TileLayerRDD[SpatialKey]]()
    }
  }

  val NLCD_MAX_ZOOM = 11

  def getMaskTiles(implicit sc:SparkContext,
                   tileReader: S3TileReader[SpatialKey, Tile],
                   layerMask: Option[LayerMaskType],
                   z:Int, x:Int, y:Int): Iterable[Tile] = {
    layerMask match {
      case Some(masks: LayerMaskType) =>
        masks map { case (layer, values) =>
          val tile = getTileWithZoom(sc, tileReader, layer, z, x, y, NLCD_MAX_ZOOM)
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
