package org.opentreemap.modeling

import geotrellis.proj4._
import geotrellis.vector._
import geotrellis.vector.io._
import geotrellis.vector.io.json._

import spray.json.JsonParser.ParsingException

trait VectorHandling {
  /** Convert GeoJson string to Polygon sequence.
    * The `polyMask` parameter expects a single GeoJson blob,
    * so this should never return a sequence with more than 1 element.
    * However, we still return a sequence, in case we want this param
    * to support multiple polygons in the future.
    */
  def parsePolyMaskParam(geoJson: String): Seq[Polygon] = {
    try {
      val featureColl = geoJson.parseGeoJson[JsonFeatureCollection]
      val polys = featureColl.getAllPolygons union
                  featureColl.getAllMultiPolygons.map(_.polygons).flatten
      polys
    } catch {
      case ex: ParsingException =>
        if (!geoJson.isEmpty)
          ex.printStackTrace(Console.err)
        Seq[Polygon]()
    }
  }

  def reprojectPolygons(polys: Seq[Polygon], srid: Int): Seq[Polygon] = {
    srid match {
      case 3857 => polys
      case 4326 => polys.map(_.reproject(LatLng, WebMercator))
      case _ => throw new ModelingException("SRID not supported.")
    }
  }

  def reprojectPoint(point: Point, srid: Int): Point = {
    srid match {
      case 3857 => point
      case 4326 => point.reproject(LatLng, WebMercator)
      case _ => throw new ModelingException("SRID not supported.")
    }
  }
}
