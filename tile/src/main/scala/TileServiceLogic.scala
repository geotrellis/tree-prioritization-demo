package org.opentreemap.modeling

import org.apache.spark._
import geotrellis.raster._
import geotrellis.vector._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.Intersects
import geotrellis.spark.io.s3._
import geotrellis.spark.render._
import geotrellis.raster.render._

trait TileServiceLogic
{
  def weightedOverlay(implicit sc: SparkContext,
                      catalog: S3LayerReader,
                      tileReader: S3ValueReader,
                      layers:Seq[String],
                      weights:Seq[Int],
                      bounds:Extent,
                      z:Int,
                      x:Int,
                      y:Int): Tile =
  {
    layers
      .zip(weights)
      .map { case (layer, weight) =>
        val tile = TileGetter.getTileWithZoom(sc, catalog, tileReader, layer, z, x, y)
        val normalizer = getNormalizer(sc, catalog, layer, null, bounds)
        tile.color(normalizer) * weight
      }
      .localAdd
  }

  def weightedOverlayForBreaks(implicit sc: SparkContext,
                               catalog: S3LayerReader,
                               layers:Seq[String],
                               weights:Seq[Int],
                               bounds:Extent): TileLayerRDD[SpatialKey] =
  {
    val rdds = layers.map { layer => getLayer(sc, catalog, layer, bounds) }
    val resultMetadata = rdds.head.metadata.copy(cellType = IntConstantNoDataCellType)
    val weightedRdds = layers
      .zip(weights)
      .zip(rdds)
      .map { case ((layer, weight), rdd) =>
        val rdd = getLayer(sc, catalog, layer, bounds)
        val normalizer = getNormalizer(sc, catalog, layer, rdd, bounds)
        val weightedRdd = rdd
          .color(normalizer)  // normalize
          .localMultiply(weight)
        ContextRDD(weightedRdd, resultMetadata)
      }
    val weightedOverlay = weightedRdds.localAdd
    ContextRDD(weightedOverlay, resultMetadata)
  }

  // Before putting a layer into a weighted overlay we normalize its values to integers 0-99.
  // We fetch all tiles within the given bounds using a coarse zoom level, bin their values to
  // find 100 quantile breaks, and return a "normalizer" -- a ColorMap which maps layer values to 0-99.
  // (Note that we're not trying to color anything, just using the mapping power of the ColorMap class.)
  //
  // We keep a cache of normalizers since they are expensive to create.
  // The cache returns the appropriate normalizer for a given layer name and treemap bounds.

  val normalizerCache = collection.mutable.Map[(String, Extent), ColorMap]()

  private def getNormalizer(implicit sc: SparkContext,
                            catalog: S3LayerReader,
                            layer:String,
                            rddOrNull: TileLayerRDD[SpatialKey],
                            bounds:Extent): ColorMap =
  {
    val key = (layer, bounds)
    if (normalizerCache.contains(key)) {
      normalizerCache(key)
    } else {
      // Fetch the RDD if we don't already have it
      val rdd = if (rddOrNull != null) rddOrNull else
        getLayer(sc, catalog, layer, bounds)
      val nBins = 100
      // TODO: use classBreaks() once https://github.com/geotrellis/geotrellis/issues/1462 is fixed
      val breaks = rdd.classBreaksExactInt(nBins)
      val normalizer = ColorMap(breaks.zipWithIndex.toMap, ColorMap.Options(noDataColor = NODATA))
      normalizerCache += (key -> normalizer)
      normalizer
    }
  }

  private def getLayer(implicit sc: SparkContext,
                       catalog: S3LayerReader,
                       layer:String,
                       bounds:Extent): TileLayerRDD[SpatialKey] = {
    val breaksZoom = 8
    val base = catalog.query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]]((layer, breaksZoom))
    base.where(Intersects(bounds)).result
  }

  def renderTile(tile: Tile, breaks: Seq[Int], colorRamp: String): Png = {
    val cr = ColorRampMap.getOrElse(colorRamp, ColorRamps.BlueToRed)
    val map = ColorMap(breaks.toArray, cr).withBoundaryType(LessThanOrEqualTo)
    tile.renderPng(map)
  }

}
