package org.opentreemap.modeling.summary

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.vector._
import geotrellis.vector.reproject._

trait PointValuesLogic {

  /** Return raster values for a sequence of WebMercator points. */
  def getValuesAtPoints(tileReader: SpatialKey => Tile, metadata: RasterMetaData)(pointsWithIds: Seq[(String, Point)]) : Seq[(String, Point, Int)] = {

    val keyedPoints: Map[SpatialKey, Seq[(String, Point)]] =
      pointsWithIds
        .map { case(id, p) =>
          // TODO, don't project if the raster is already in WebMercator
          metadata.mapTransform(p.reproject(WebMercator, metadata.crs)) -> (id, p)
        }
        .groupBy(_._1)
        .map { case(k, pairs) => k -> pairs.map(_._2) }

    keyedPoints.map { case(k, points) =>
      val raster = Raster(tileReader(k), metadata.mapTransform(k))
      points.map { case(id, p) =>
        // TODO, don't project if the raster is already in WebMercator
        val projP = p.reproject(WebMercator, metadata.crs)
        val (col, row) = raster.rasterExtent.mapToGrid(projP.x, projP.y)
        (id, p, raster.tile.get(col,row))
      }
    }.toSeq.flatten
  }


}
