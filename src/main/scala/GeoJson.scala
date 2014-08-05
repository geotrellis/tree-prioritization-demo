// package org.opentreemap.modeling

// import java.io.File
// import scala.io.Source
// import geotrellis.data.geojson.GeoJsonReader
// import com.vividsolutions.jts.{ geom => jts }

// class GeoJson(geoJson: String) {
//   def toGeometry: Option[jts.Geometry] =
//     if (geoJson == "")
//       None
//     else
//       Some(toGeometry(geoJson))

//   override def toString = geoJson

//   private def toGeometry(geoJson: String): jts.Geometry = {
//     GeoJsonReader.parse(geoJson) match {
//       case Some(geomArray) if (geomArray.length > 0) =>
//         geomArray.head.geom match {
//           case p: jts.Polygon => transform(p)
//           case p: jts.MultiPolygon => transform(p)
//           case _ =>
//             throw new Exception(s"GeoJSON format not supported: $geoJson")
//         }
//       case _ =>
//         throw new Exception(s"Invalid GeoJSON: $geoJson")
//     }
//   }

//   private def transform(geom: jts.Geometry): jts.Geometry = {
//     // GeoTrellis expects coords in (lat, lng) format instead of the (lng, lat)
//     // format that is standard in the GeoJSON spec.
//     Transformer.swapCoords(geom)
//     Transformer.transform(geom, Projections.LatLong, Projections.WebMercator)
//   }
// }

