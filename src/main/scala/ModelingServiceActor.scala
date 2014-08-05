package org.opentreemap.modeling

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.stats._
import geotrellis.services._
import geotrellis.vector._
import geotrellis.vector.json._
import geotrellis.vector.reproject._
import geotrellis.proj4._
import geotrellis.engine._
import geotrellis.engine.op.local._
import geotrellis.engine.op.zonal.summary._
import geotrellis.engine.render._
import geotrellis.engine.stats._

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

class ModelingServiceActor() extends Actor with ModelingService {
  def actorRefFactory = context
  def receive = runRoute(serviceRoute)
}

trait ModelingService extends HttpService {
  implicit def executionContext = actorRefFactory.dispatcher

  def getPolygons(mask: String): Seq[Polygon] = {
    import spray.json.DefaultJsonProtocol._

    mask
      .parseGeoJson[JsonFeatureCollection]
      .getAllPolygons
      .map(_.reproject(LatLng, WebMercator))
  }

  lazy val serviceRoute =
    indexRoute ~
    colorsRoute ~
    breaksRoute ~
    overlayRoute ~
    histogramRoute ~
    staticRoute

  lazy val indexRoute = pathSingleSlash {
    getFromFile(ServiceConfig.staticPath + "/index.html")
  }

  lazy val colorsRoute = path("gt" / "colors") {
    complete(ColorRampMap.getJson)
  }

  lazy val breaksRoute = path("gt" / "breaks") {
    parameters('layers,
               'weights,
               'numBreaks.as[Int],
               'mask ? "") {
      (layersParam, weightsParam, numBreaks, mask) => {
        // TODO: Read extent from query string (bbox).
        val extent = Extent(-19840702.0356, 2143556.8396, -7452702.0356, 11537556.8396)
        // TODO: Dynamic breaks based on configurable breaks resolution.
        val re = RasterExtent(extent, 256, 256)

        val layers = layersParam.split(",")
        val weights = weightsParam.split(",").map(_.toInt)

        val model = {
          val unmasked = Model.weightedOverlay(layers, weights, re)

          if(mask != "")
            unmasked.mask(getPolygons(mask))
          else 
            unmasked
        }

        model
          .classBreaks(numBreaks)
          .run match {
            case Complete(breaks, _) =>
              val breaksArray = breaks.mkString("[", ",", "]")
              val json = s"""{ "classBreaks" : $breaksArray }"""
              complete(json)
            case Error(message, trace) =>
              failWith(new RuntimeException(message))
          }
      }
    }
  }

  lazy val overlayRoute = path("gt" / "wo") {
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
               'breaks,
               'colorRamp ? "blue-to-red",
               'mask ? "") {
        (_, _, _, _, bbox, cols, rows, layersString, weightsString,
          palette, breaksString, colorRamp, mask) => {
          val extent = Extent.fromString(bbox)
          val re = RasterExtent(extent, cols, rows)

          val layers = layersString.split(",")
          val weights = weightsString.split(",").map(_.toInt)
          val breaks = breaksString.split(",").map(_.toInt)

          val model = {
            val unmasked = Model.weightedOverlay(layers, weights, re)

            if(mask != "")
              unmasked.mask(getPolygons(mask))
            else
              unmasked
          }

          val cr = ColorRampMap.getOrElse(colorRamp, ColorRamps.BlueToRed)
          val ramp =
            if (cr.toArray.length < breaks.length) cr.interpolate(breaks.length)
            else cr

          val png: ValueSource[Png] = model.renderPng(ramp, breaks)

          png.run match {
            case Complete(img, h) =>
              respondWithMediaType(MediaTypes.`image/png`) {
                complete(img.bytes)
              }
            case Error(message, trace) =>
              failWith(new RuntimeException(message))
          }
      }
    }
  }

  lazy val histogramRoute = path("gt" / "histogram") {
    parameters('layer, 'mask ? "") {
      (layerParam, mask) => {
        val start = System.currentTimeMillis()

        val rs = RasterSource(layerParam)
        val summary: ValueSource[Histogram] =
          if(mask != "") {
            val maskPolygons = getPolygons(mask)
            println(maskPolygons.toSeq)
            val histograms = 
              DataSource.fromSources(
                maskPolygons.map { p =>
                  rs.zonalHistogram(p)
                }
              )
            histograms.converge { seq => FastMapHistogram.fromHistograms(seq) }
          } else {
            rs.histogram
          }

        summary.run match {
          case Complete(result, h) =>
            val elapsedTotal = System.currentTimeMillis - start
            val histogram = result.toJSON
            val data =
              s"""{
                "elapsed": "$elapsedTotal",
                "histogram": $histogram
                  }"""
            complete(data)
          case Error(message, trace) =>
            failWith(new RuntimeException(message))
        }
      }
    }
  }

  lazy val staticRoute = pathPrefix("") {
    getFromDirectory(ServiceConfig.staticPath)
  }
}

