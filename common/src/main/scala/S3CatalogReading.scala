package org.opentreemap.modeling

import org.apache.spark._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io.Intersects
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.vector._


trait S3CatalogReading {
  final val bucket = "azavea-datahub"
  final val prefix = "catalog"


  var _catalog: S3LayerReader = null
  def catalog(implicit sc: SparkContext): S3LayerReader = {
    // we want to re-use the reference because AttributeStore performs caching in look-ups required for each request.
    // this saves ~200ms per request
    if (null == _catalog) {
      val attributeStore = new S3AttributeStore(bucket, prefix)
      _catalog = new S3ProxyLayerReader(attributeStore)
    }
    _catalog
  }

  var _tileReader: S3ValueReader = null
  def tileReader(implicit sc: SparkContext):  S3ValueReader = {
    if (null == _tileReader) {
      _tileReader = S3ProxyValueReader(bucket, prefix)
    }
    _tileReader
  }

  def metadata(implicit sc: SparkContext, layerId: LayerId): TileLayerMetadata[SpatialKey] =
     catalog(sc)
      .attributeStore
      .readMetadata[TileLayerMetadata[SpatialKey]](layerId)

  def queryAndCropLayer(implicit sc: SparkContext, layerId: LayerId, extent: Extent): TileLayerRDD[SpatialKey] = {
    catalog.query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]](layerId)
      .where(Intersects(extent))
      .result
  }

}
