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
}
