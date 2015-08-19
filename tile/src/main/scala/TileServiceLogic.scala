package org.opentreemap.modeling.tile

import org.apache.spark._

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.services._
import geotrellis.spark._
import geotrellis.spark.io.s3._
import geotrellis.spark.tiling._
import geotrellis.raster.resample._
import geotrellis.raster.render._
import geotrellis.spark.op.local._

trait TileServiceLogic {
  val DEFAULT_ZOOM = 11
  val BREAKS_ZOOM = 8

  def addTiles(t1: Tile, t2: Tile): Tile = {
    t1.combine(t2)(_ + _)
  }

  def weightedOverlay(implicit sc: SparkContext,
                      catalog: S3RasterCatalog,
                      layers:Seq[String],
                      weights:Seq[Int],
                      z:Int,
                      x:Int,
                      y:Int): Tile = {
    layers
      .zip(weights)
      .map { case (layer, weight) =>
        // TODO: Handle layers with different pyramids instead of using
        // DEFAULT_ZOOM
        val tile =
          if (z <= DEFAULT_ZOOM) {
            val reader = catalog.tileReader[SpatialKey]((layer, z))
            reader(SpatialKey(x, y))
          } else {
            val layerId = LayerId(layer, DEFAULT_ZOOM)
            val reader = catalog.tileReader[SpatialKey](layerId)
            val rmd = catalog.getLayerMetadata(layerId).rasterMetaData
            val layoutLevel = ZoomedLayoutScheme().levelFor(z)
            val mapTransform = MapKeyTransform(rmd.crs, layoutLevel.tileLayout.layoutCols, layoutLevel.tileLayout.layoutRows)
            val targetExtent = mapTransform(x, y)
            val gb @ GridBounds(nx, ny, _, _) = rmd.mapTransform(targetExtent)
            val sourceExtent = rmd.mapTransform(nx, ny)
            val largerTile = reader(SpatialKey(nx, ny))
            largerTile.resample(sourceExtent, RasterExtent(targetExtent, 256, 256))
          }
        // Convert Byte tiles to Int so that math operations do not overflow
        tile.convert(TypeInt).map(_ * weight)
    }
      .reduce(addTiles)
  }

  def weightedOverlay(implicit sc: SparkContext,
                      catalog: S3RasterCatalog,
                      layers:Seq[String],
                      weights:Seq[Int],
                      bounds:Extent): RasterRDD[SpatialKey] = {
    layers
      .zip(weights)
      .map { case (layer, weight) =>
        val base = catalog.query[SpatialKey]((layer, BREAKS_ZOOM))
        val intersected = base.where(Intersects(bounds))
        val rdd = intersected.toRDD
        // Convert Byte RDD to Int so that math operations do not overflow
        rdd.convert(TypeInt).localMultiply(weight)
      }
      .localAdd
  }

  def renderTile(tile: Tile, breaks: Seq[Int], colorRamp: String): Png = {
    val ramp = {
      val cr = ColorRampMap.getOrElse(colorRamp, ColorRamps.BlueToRed)
      cr.interpolate(breaks.length)
    }
    tile.renderPng(ramp.toArray, breaks.toArray)
  }

}
