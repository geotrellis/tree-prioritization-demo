package org.opentreemap.modeling

import geotrellis.proj4._
import geotrellis.vector._
import geotrellis.vector.io._
import geotrellis.vector.io.json._
import spray.json.JsonParser.ParsingException

trait VectorHandling {

  var _zipCodes:Map[String, String] = null

  lazy val zipCodes:Map[String, String] = {
    if (_zipCodes == null) {
      var zipCodeMap = scala.collection.mutable.Map[String, String]()
      val zipCodeStream = getClass.getResourceAsStream("/masks/zip-codes.tsv")
      val source = scala.io.Source.fromInputStream(zipCodeStream)
      try {
        for (line <- source.getLines) {
          val columns = line.split("\t")
          zipCodeMap += (columns(0) -> columns(1))
        }
      } finally {
        source.close
      }
      _zipCodes = zipCodeMap.toMap
    }
    _zipCodes
  }

  def parseZipCodesParam(zipCodeParam: String): Seq[Polygon] = {
    try {
      if (zipCodeParam.nonEmpty) {
        zipCodeParam.split(",")
          .map(zipCodes(_))
          .map(_.parseGeoJson[Polygon])
      } else {
        Seq[Polygon]()
      }
    } catch {
      case ex: ParsingException =>
        if (!zipCodeParam.isEmpty)
          ex.printStackTrace(Console.err)
        Seq[Polygon]()
    }
  }

  def inBounds(boundary: String, bbox: String): Boolean = {
    val extent = Extent.fromString(bbox)
    val poly = boundary.parseGeoJson[Polygon]
    clipExtentToExtentOfPolygons(extent, List(poly)) != None
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

  def clipExtentToExtentOfPolygons(extent: Extent, polys: Seq[Polygon]): Option[Extent] = {
    if (polys.isEmpty) {
      Option(extent)
    } else {
      extent intersection polys.map(_.envelope).reduce(_ combine _)
    }
  }
}
