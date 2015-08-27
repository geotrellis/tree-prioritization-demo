package org.opentreemap.modeling

import java.io.File

// Importing KeyCodecs._ avoids this exception on compile:
//   could not find implicit value for evidence parameter of type
//   geotrellis.spark.io.avro.AvroRecordCodec[geotrellis.spark.SpatialKey]
import geotrellis.spark.io.avro.KeyCodecs._

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io.s3.{S3RasterCatalog, CachingRasterRDDReader}
import geotrellis.spark.op.local._
import geotrellis.vector._

import org.apache.spark._

trait S3CatalogReading {
  import ModelingTypes._

  var _catalog: S3RasterCatalog = null

  def catalog(implicit sc: SparkContext): S3RasterCatalog = {
    // we want to re-use the reference because AttributeStore performs caching in look-ups required for each request.
    // this saves ~200ms per request
    if (null != _catalog)
      _catalog
    else {
      _catalog = S3RasterCatalog("com.azavea.datahub", "catalog")(sc)
      _catalog
    }
  }

  def cacheDirectory: String = { "/tmp" }

  implicit val reader = new CachingRasterRDDReader[SpatialKey](new File(cacheDirectory))

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
