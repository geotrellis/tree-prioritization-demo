package org.opentreemap.modeling

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.Intersects
import geotrellis.spark.io.s3._
import geotrellis.spark.render._
import geotrellis.raster.render._
import geotrellis.raster.mapalgebra.local.Subtract

trait TileServiceLogic { self: ReaderSet with TileGetter =>
  val normalizedBins = 100

  def weightedOverlay(layers:Seq[String],
                      weights:Seq[Int],
                      bounds:Extent,
                      z:Int,
                      x:Int,
                      y:Int): Tile =
  {
    layers
      .zip(weights)
      .map { case (layer, weight) =>
        val tile = getTileWithZoom(layer, z, x, y)
        val normalizer = getNormalizer(layer, null, bounds)
        val normalizedTile = tile.color(normalizer).convert(IntConstantNoDataCellType)
        val polarizedTile =
          if (weight.signum < 0) {
            Subtract(normalizedBins - 1, normalizedTile)
          } else {
            normalizedTile
          }
        polarizedTile * weight.abs
      }
      .localAdd
  }

  def weightedOverlayForBreaks(layerNames:Seq[String],
                               weights:Seq[Int],
                               bounds:Extent,
                               clippedBounds:Extent): TileLayerCollection[SpatialKey] =
  {
    val layers = layerNames.map { layerName => getLayer(layerName, clippedBounds) }
    val resultMetadata = layers.head.metadata.copy(cellType = IntConstantNoDataCellType)
    val weightedLayers =
      layerNames
        .zip(weights)
        .zip(layers)
        .map { case ((layerName, weight), layer) =>
          val normalizer = getNormalizer(layerName, layer, bounds)
          val normalizedLayer =
            ContextCollection(layer.map { case (k, v) => (k, v.color(normalizer)) }, resultMetadata)
              .convert(IntConstantNoDataCellType)
          val polarizedLayer =
            if (weight.signum < 0) {
              normalizedLayer.localSubtractFrom(normalizedBins - 1)
            } else {
              normalizedLayer
            }
          val weightedLayer = polarizedLayer.localMultiply(weight.abs)

          ContextCollection(weightedLayer, resultMetadata)
        }
    val weightedOverlay = weightedLayers.localAdd
    ContextCollection(weightedOverlay, resultMetadata)
  }

  // Before putting a layer into a weighted overlay we normalize its values to integers 0-99.
  // We fetch all tiles within the given bounds using a coarse zoom level, bin their values to
  // find 100 quantile breaks, and return a "normalizer" -- a ColorMap which maps layer values to 0-99.
  // (Note that we're not trying to color anything, just using the mapping power of the ColorMap class.)
  //
  // We keep a cache of normalizers since they are expensive to create.
  // The cache returns the appropriate normalizer for a given layer name and treemap bounds.

  val normalizerCache = collection.mutable.Map[(String, Extent), ColorMap]()

  private def getNormalizer(layerName:String,
                            layerOrNull: TileLayerCollection[SpatialKey],
                            bounds:Extent): ColorMap =
  {
    val key = (layerName, bounds)
    if (normalizerCache.contains(key)) {
      normalizerCache(key)
    } else {
      // Fetch the layer if we don't already have it
      val layer = if (layerOrNull != null) layerOrNull else
        getLayer(layerName, bounds)
      val breaks = layer.classBreaksExactInt(normalizedBins)
      val normalizer = ColorMap(breaks.zipWithIndex.toMap, ColorMap.Options(noDataColor = NODATA))
      normalizerCache += (key -> normalizer)
      normalizer
    }
  }

  private def getLayer(layerName:String,
                       bounds:Extent): TileLayerCollection[SpatialKey] =
    collectionReader
      .query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]]((layerName, breaksZoom))
      .where(Intersects(bounds))
      .result

  def renderTile(tile: Tile, breaks: Seq[Int], colorRamp: String): Png = {
    val cr = ColorRampMap.getOrElse(colorRamp, ColorRamps.BlueToRed)
    val map = ColorMap(breaks.toArray, cr)
    tile.renderPng(map)
  }

}
