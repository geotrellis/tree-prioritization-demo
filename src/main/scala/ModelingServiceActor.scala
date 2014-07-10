package org.opentreemap.modeling

import geotrellis.process
import geotrellis.Extent
import geotrellis.RasterExtent
import geotrellis.Png
import geotrellis.source.ValueSource
import geotrellis.data.geojson.GeoJsonReader
import geotrellis.render.ColorRamps
import geotrellis.services.ColorRampMap

import java.io.File
import org.parboiled.common.FileUtils
import akka.actor.Actor
import akka.pattern.ask
import spray.routing.HttpService
import spray.routing.RequestContext
import spray.routing.directives.CachingDirectives
import spray.can.server.Stats
import spray.can.Http
import spray.httpx.marshalling.Marshaller
import spray.httpx.encoding.Gzip
import spray.http.MediaTypes

import com.vividsolutions.jts.{ geom => jts }

class ModelingServiceActor(val staticPath: String) extends Actor with ModelingService {
  def actorRefFactory = context
  def receive = runRoute(serviceRoute)
}

trait ModelingService extends HttpService {
  implicit def executionContext = actorRefFactory.dispatcher

  val staticPath: String

  val serviceRoute = {
    pathSingleSlash {
      getFromFile(staticPath + "/index.html")
    } ~
    pathPrefix("gt") {
      path("colors") {
        complete(ColorRampMap.getJson)
      } ~
      path("breaks") {
        parameters('layers,
                   'weights,
                   'numBreaks.as[Int],
                   'mask ? "") {
          (layersParam, weightsParam, numBreaks, mask) => {
            val extent = Extent(-19840702.0356, 2143556.8396, -7452702.0356, 11537556.8396)
            val re = RasterExtent(extent, 256, 256)

            val layers = layersParam.split(",")
            val weights = weightsParam.split(",").map(_.toInt)

            Model.weightedOverlay(layers, weights, re)
              .classBreaks(numBreaks)
              .run match {
                case process.Complete(breaks, _) =>
                  val breaksArray = breaks.mkString("[", ",", "]")
                  val json = s"""{ "classBreaks" : $breaksArray }"""
                  complete(json)
                case process.Error(message, trace) =>
                  failWith(new RuntimeException(message))
              }
          }
        }
      } ~
      path("wo") {
        parameters('service,
                   'request,
                   'version,
                   'format,
                   'bbox,
                   'height.as[Int],
                   'width.as[Int],
                   'layers,
                   'weights,
                   'palette ? "ff0000,ffff00,00ff00,0000ff",
                   'colors.as[Int] ? 4,
                   'breaks,
                   'colorRamp ? "blue-to-red",
                   'mask ? "") {
          (_, _, _, _, bbox, cols, rows, layersString, weightsString,
            palette, colors, breaksString, colorRamp, mask) => {
            val extent = Extent.fromString(bbox)

            val re = RasterExtent(extent, cols, rows)

            val layers = layersString.split(",")
            val weights = weightsString.split(",").map(_.toInt)
            val breaks = breaksString.split(",").map(_.toInt)

            val model = Model.weightedOverlay(layers, weights, re)

            val overlay = if (mask == "") model else model.mask(parseGeoJson(mask))

            val cr = ColorRampMap.getOrElse(colorRamp, ColorRamps.BlueToRed)
            val ramp =
              if (cr.toArray.length < breaks.length) cr.interpolate(breaks.length)
              else cr

            val png: ValueSource[Png] = overlay.renderPng(ramp, breaks)

            png.run match {
              case process.Complete(img, h) =>
                respondWithMediaType(MediaTypes.`image/png`) {
                  complete(img)
                }
              case process.Error(message, trace) =>
                println(message)
                println(trace)
                println(re)
                failWith(new RuntimeException(message))
            }
          }
        }
      } ~
      path("sum") {
        parameters('polygon,
                   'layers,
                   'weights) {
          (polygonJson, layersString, weightsString) => {
            val start = System.currentTimeMillis()

            val layers = layersString.split(",")
            val weights = weightsString.split(",").map(_.toInt)

            var poly = parseGeoJson(polygonJson)
            val summary = Model.summary(layers, weights, poly)

            summary.run match {
              case process.Complete(result, h) =>
                val elapsedTotal = System.currentTimeMillis - start

                val layerSummaries =
                  "[" + result.layerSummaries.map { ls =>
                    val v = "%.2f".format(ls.score * 100)
                    s"""{ "layer": "${ls.name}", "total": "${v}" }"""
                  }.mkString(",") + "]"

                val totalVal = "%.2f".format(result.score * 100)
                val data =
                  s"""{
                        "layerSummaries": $layerSummaries,
                        "total": "${totalVal}",
                        "elapsed": "$elapsedTotal"
                      }"""
                complete(data)
              case process.Error(message, trace) =>
                failWith(new RuntimeException(message))
            }
          }
        }
      }
    } ~
    pathPrefix("") {
      getFromDirectory(staticPath)
    }
  }

  def parseGeoJson(geoJson: String): jts.Polygon = {
    GeoJsonReader.parse(geoJson) match {
      case Some(geomArray) if (geomArray.length > 0) =>
        geomArray.head.geom match {
          case p: jts.Polygon =>
            Transformer.transform(p, Projections.LatLong, Projections.WebMercator)
              .asInstanceOf[jts.Polygon]
          case _ =>
            throw new Exception(s"Invalid GeoJSON: $geoJson")
        }
      case _ =>
        throw new Exception(s"Invalid GeoJSON: $geoJson")
    }
  }
}

