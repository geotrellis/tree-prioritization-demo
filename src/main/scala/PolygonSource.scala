package org.opentreemap.modeling

import java.io.File
import scala.io.Source
import geotrellis.data.geojson.GeoJsonReader
import com.vividsolutions.jts.{ geom => jts }

trait PolygonSource {
  def getGeoJson(layerName: String, featureId: String): Option[GeoJson]
}

class FilePolygonSource(featuresPath: String) extends PolygonSource {
  override def getGeoJson(layerName: String, featureId: String): Option[GeoJson] = {
    val rootDir = new File(featuresPath)
    val layerDir = new File(rootDir, layerName)
    val featureFile = new File(layerDir, featureId + ".geojson")
    val source = Source.fromFile(featureFile)
    val geoJson = source.getLines.mkString
    Some(new GeoJson(geoJson))
  }
}

class StringPolygonSource(geoJson: String) extends PolygonSource {
  override def getGeoJson(layerName: String, featureId: String): Option[GeoJson] = {
    return Some(new GeoJson(geoJson))
  }
}

class GeoJson(geoJson: String) {
  def toGeometry: Option[jts.Geometry] =
    if (geoJson == "")
      None
    else
      Some(toGeometry(geoJson))

  override def toString = geoJson

  private def toGeometry(geoJson: String): jts.Geometry = {
    GeoJsonReader.parse(geoJson) match {
      case Some(geomArray) if (geomArray.length > 0) =>
        geomArray.head.geom match {
          case p: jts.Polygon => transform(p)
          case p: jts.MultiPolygon => transform(p)
          case _ =>
            throw new Exception(s"GeoJSON format not supported: $geoJson")
        }
      case _ =>
        throw new Exception(s"Invalid GeoJSON: $geoJson")
    }
  }

  private def transform(geom: jts.Geometry): jts.Geometry = {
    Transformer.transform(geom, Projections.LatLong, Projections.WebMercator)
  }
}

