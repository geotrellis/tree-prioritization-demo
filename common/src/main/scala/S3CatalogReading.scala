package org.opentreemap.modeling

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io.s3._
import geotrellis.spark.op.local._
import geotrellis.vector._

import org.apache.spark._

trait S3CatalogReading {
  import ModelingTypes._

  def catalog(implicit sc: SparkContext): S3RasterCatalog = {
    S3RasterCatalog("com.azavea.datahub", "catalog")(sc)
  }

  def queryAndCropLayer(implicit sc: SparkContext, layerId: LayerId, extent: Extent): RasterRDD[SpatialKey] = {
    catalog.query[SpatialKey](layerId)
      .where(Intersects(extent))
      .toRDD
  }

 /** Convert `layerMask` map to list of filtered rasters.
    * The result contains a raster for each layer specified,
    * and that raster only contains whitelisted values present
    * in the `layerMask` argument.
    */
  def parseLayerMaskParam(implicit sc:SparkContext,
                          layerMask: Option[LayerMaskType],
                          extent: Extent,
                          zoom: Int): Iterable[RasterRDD[SpatialKey]] = {
    layerMask match {
      case Some(masks: LayerMaskType) =>
        masks map { case (layerName, values) =>
          catalog.query[SpatialKey]((layerName, zoom))
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

  def parseLayerTileMaskParam(implicit sc:SparkContext,
    layerMask: Option[LayerMaskType],
    z:Int, x:Int, y:Int): Iterable[Tile] = {
    layerMask match {
      case Some(masks: LayerMaskType) =>
        masks map { case (layer, values) =>
          val reader = catalog.tileReader[SpatialKey]((layer, z))
          val tile = reader(SpatialKey(x, y))
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
