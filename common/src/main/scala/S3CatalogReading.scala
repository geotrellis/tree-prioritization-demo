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
      // _catalog = new S3ProxyLayerReader(attributeStore)
      _catalog = new S3LayerReader(attributeStore)
    }
    _catalog
  }

  var _tileReader: S3ValueReader = null
  def tileReader(implicit sc: SparkContext):  S3ValueReader = {
    if (null == _tileReader) {
      // _tileReader = S3ProxyValueReader(bucket, prefix)
      _tileReader = S3ValueReader(bucket, prefix)
    }
    _tileReader
  }

  // TODO: Eliminate these "NoProxy" versions once the summary service container knows about the s3-proxy-cache container

  var _catalogNoProxy: S3LayerReader = null
  def catalogNoProxy(implicit sc: SparkContext): S3LayerReader = {
    if (null == _catalogNoProxy) {
      val attributeStore = new S3AttributeStore(bucket, prefix)
      _catalogNoProxy = new S3LayerReader(attributeStore)
    }
    _catalogNoProxy
  }

  var _tileReaderNoProxy: S3ValueReader = null
  def tileReaderNoProxy(implicit sc: SparkContext):  S3ValueReader = {
    if (null == _tileReaderNoProxy) {
      _tileReaderNoProxy = S3ValueReader(bucket, prefix)
    }
    _tileReaderNoProxy
  }

  def metadata(implicit sc: SparkContext, layerId: LayerId): TileLayerMetadata[SpatialKey] =
     catalog(sc)
      .attributeStore
      .readMetadata[TileLayerMetadata[SpatialKey]](layerId)

  def queryAndCropLayer(implicit sc: SparkContext, layerId: LayerId, extent: Extent): TileLayerRDD[SpatialKey] = {
    catalogNoProxy.query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]](layerId)
      .where(Intersects(extent))
      .result
  }

}
