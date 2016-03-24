package org.opentreemap.modeling

import java.io.File

import org.apache.avro._
import org.apache.spark._

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.Intersects
import geotrellis.spark.io.index._
import geotrellis.spark.io.json._
import geotrellis.spark.io.s3.{S3LayerReader, S3TileReader, S3LayerHeader}
import geotrellis.vector._


trait S3CatalogReading {
  import ModelingTypes._

  final val bucket = "azavea-datahub"
  final val prefix = "catalog"

  var _catalog: S3LayerReader[SpatialKey, Tile, RasterRDD[SpatialKey]] = null
  def catalog(implicit sc: SparkContext): S3LayerReader[SpatialKey, Tile, RasterRDD[SpatialKey]] = {
    // we want to re-use the reference because AttributeStore performs caching in look-ups required for each request.
    // this saves ~200ms per request
    if (null != _catalog)
      _catalog
    else {
      _catalog = S3LayerReader[SpatialKey, Tile, RasterRDD](bucket, prefix, None)
      _catalog
    }
  }

  var _tileReader: S3TileReader[SpatialKey, Tile] = null
  def tileReader(implicit sc: SparkContext):  S3TileReader[SpatialKey, Tile] = {
    if (null != _tileReader)
      _tileReader
    else {
      _tileReader = S3TileReader[SpatialKey, Tile](bucket, prefix)
      _tileReader
    }
  }

  def metadata(implicit sc: SparkContext, layerId: LayerId): RasterMetaData =
     catalog(sc)
      .attributeStore
      .readLayerAttributes[S3LayerHeader, RasterMetaData, KeyBounds[SpatialKey], KeyIndex[SpatialKey], Schema](layerId)._2

  def queryAndCropLayer(implicit sc: SparkContext, layerId: LayerId, extent: Extent): RasterRDD[SpatialKey] = {
    catalog.query(layerId)
      .where(Intersects(extent))
      .toRDD
  }

}
