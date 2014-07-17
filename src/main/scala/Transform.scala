package org.opentreemap.modeling

import geotrellis.feature.Geometry

import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.opengis.referencing.crs.{CoordinateReferenceSystem => Crs}
import org.opengis.referencing.operation.MathTransform

import scala.collection.mutable

import com.vividsolutions.jts.{ geom => jts }

object Transformer {
  private val transformCache: mutable.Map[(Crs, Crs), MathTransform] =
    new mutable.HashMap()

  initCache()

  private def initCache() {
    cacheTransform(Projections.LatLong, Projections.WebMercator)
    cacheTransform(Projections.WebMercator, Projections.LatLong)
  }

  private def cacheTransform(crs1: Crs, crs2: Crs) {
    transformCache((crs1, crs2)) = CRS.findMathTransform(crs1, crs2, true)
  }

  def transform[D](feature: Geometry[D], fromCRS: Crs, toCRS: Crs): Geometry[D] =
    feature.mapGeom(geom => transform(geom, fromCRS, toCRS))

  def transform[D](geom: jts.Geometry, fromCRS: Crs, toCRS: Crs): jts.Geometry = {
    if (!transformCache.contains((fromCRS, toCRS))) {
      cacheTransform(fromCRS, toCRS)
    }
    JTS.transform(geom, transformCache((fromCRS, toCRS)))
  }

  def swapCoords(geom: jts.Geometry) {
    geom.apply(SwapCoordsFilter)
  }

  private object SwapCoordsFilter extends jts.CoordinateFilter {
    override def filter(coord: jts.Coordinate) {
      val x = coord.x
      coord.x = coord.y
      coord.y = x
    }
  }
}

