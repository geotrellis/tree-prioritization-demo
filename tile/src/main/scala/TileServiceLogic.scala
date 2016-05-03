package org.opentreemap.modeling

import org.apache.spark._
import geotrellis.raster._
import geotrellis.vector._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.Intersects
import geotrellis.spark.io.s3._
import geotrellis.raster.render._

trait TileServiceLogic
{
  val BREAKS_ZOOM = 8

  def addTiles(t1: Tile, t2: Tile): Tile = {
    t1.combine(t2)(_ + _)
  }

  def weightedOverlay(implicit sc: SparkContext,
                      catalog: S3LayerReader,
                      tileReader: S3ValueReader,
                      layers:Seq[String],
                      weights:Seq[Int],
                      z:Int,
                      x:Int,
                      y:Int): Tile = {
    val tiles = layers.map { layer =>
      TileGetter.getTileWithZoom(sc, catalog, tileReader, layer, z, x, y)
    }
    val anyFloats = tiles.exists(tile => tile.cellType.isFloatingPoint)
    val targetCellType = if (anyFloats) FloatConstantNoDataCellType else IntConstantNoDataCellType
    tiles
      .zip(weights)
      .map { case (tile, weight) =>
        val convertedTile = if (tile.cellType == targetCellType) tile else tile.convert(targetCellType)
        convertedTile.map(_ * weight)
      }
      .reduce(addTiles)
  }

  def weightedOverlay(implicit sc: SparkContext,
                      catalog: S3LayerReader,
                      layers:Seq[String],
                      weights:Seq[Int],
                      bounds:Extent): TileLayerRDD[SpatialKey] = {
    val rdds = layers.map { layer =>
      val base = catalog.query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]]((layer, BREAKS_ZOOM))
      base.where(Intersects(bounds)).result
    }
    val anyFloats = rdds.exists(rdd => rdd.metadata.cellType.isFloatingPoint)
    val targetCellType = if (anyFloats) FloatConstantNoDataCellType else IntConstantNoDataCellType
    val weightedRdds = rdds
      .zip(weights)
      .map { case (rdd, weight) =>
        // Convert layer cell types for two reasons:
        // 1) So a Byte layer won't overflow when weighted
        // 2) To normalize cell types before adding them
        val convertedRdd = if (rdd.metadata.cellType == targetCellType) rdd else rdd.convert(targetCellType)
        convertedRdd.withContext {
          _.localMultiply(weight)
        }
      }
    val weightedOverlay = weightedRdds.localAdd
    ContextRDD(weightedOverlay, weightedRdds.head.metadata)
  }

  def renderTile(tile: Tile, breaks: Seq[Int], colorRamp: String): Png = {
    val cr = ColorRampMap.getOrElse(colorRamp, ColorRamps.BlueToRed)
    val map = ColorMap(breaks.toArray, cr)
    tile.renderPng(map)
  }

}
