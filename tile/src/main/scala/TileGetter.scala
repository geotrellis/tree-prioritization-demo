package org.opentreemap.modeling

import org.apache.avro._

import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.spark._
import geotrellis.spark.io.Intersects
import geotrellis.spark.io.index._
import geotrellis.spark.io.json._
import geotrellis.spark.io.s3.{S3LayerReader, S3TileReader, S3LayerHeader}
import geotrellis.spark.op.local._
import geotrellis.spark.tiling._
import geotrellis.vector._

import org.apache.spark._

object TileGetter {
  import ModelingTypes._

  def _getMetadata(implicit sc: SparkContext, tileReader: S3TileReader[SpatialKey, Tile], layerId: LayerId): RasterMetaData =
     tileReader
      .attributeStore
      .readLayerAttributes[S3LayerHeader, RasterMetaData, KeyBounds[SpatialKey], KeyIndex[SpatialKey], Schema](layerId)._2

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
                          catalog: S3LayerReader[SpatialKey, Tile, RasterRDD[SpatialKey]],
                          layerMask: Option[LayerMaskType],
                          extent: Extent,
    zoom: Int): Iterable[RasterRDD[SpatialKey]] = {
    layerMask match {
      case Some(masks: LayerMaskType) =>
        masks map { case (layerName, values) =>
          catalog.query((layerName, zoom))
          .where(Intersects(extent))
          .toRDD
          .localMap { z =>
            if (values contains z) z
            else NODATA
          }

        }
      case None =>
        Seq[RasterRDD[SpatialKey]]()
    }
  }

  def getMaskTiles(implicit sc:SparkContext,
                   tileReader: S3TileReader[SpatialKey, Tile],
                   layerMask: Option[LayerMaskType],
                   z:Int, x:Int, y:Int): Iterable[Tile] = {
    layerMask match {
      case Some(masks: LayerMaskType) =>
        masks map { case (layer, values) =>
          val reader = tileReader.read((layer, z))
          val tile = reader.read(SpatialKey(x, y))
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
