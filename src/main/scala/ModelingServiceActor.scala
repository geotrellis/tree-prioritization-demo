package org.opentreemap.modeling

import geotrellis.process
import geotrellis.Extent
import geotrellis.RasterExtent
import geotrellis.Png
import geotrellis.source.ValueSource
import geotrellis.source.RasterSource
import geotrellis.render.ColorRamps
import geotrellis.services.ColorRampMap
import geotrellis.feature.Polygon

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

  def applyMask(model: RasterSource, mask: GeoJson): RasterSource = {
    mask.toGeometry match {
      case Some(p: jts.Polygon) => model.mask(p)
      case Some(p: jts.MultiPolygon) => model.mask(p)
      case _ => model
    }
  }

  val indexRoute = pathSingleSlash {
    getFromFile(ServiceConfig.staticPath + "/index.html")
  }

  val colorsRoute = path("gt" / "colors") {
    complete(ColorRampMap.getJson)
  }

  val breaksRoute = path("gt" / "breaks") {
    parameters('layers,
               'weights,
               'numBreaks.as[Int],
               'mask ? "") {
      (layersParam, weightsParam, numBreaks, maskParam) => {
        // TODO: Read extent from query string (bbox).
        val extent = Extent(-19840702.0356, 2143556.8396, -7452702.0356, 11537556.8396)
        // TODO: Dynamic breaks based on configurable breaks resolution.
        val re = RasterExtent(extent, 256, 256)

        val layers = layersParam.split(",")
        val weights = weightsParam.split(",").map(_.toInt)

        val model = Model.weightedOverlay(layers, weights, re)
        val mask = new GeoJson(maskParam)
        val overlay = applyMask(model, mask)

        overlay
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
  }

  val overlayRoute = path("gt" / "wo") {
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
          palette, breaksString, colorRamp, maskParam) => {
          val extent = Extent.fromString(bbox)
          val re = RasterExtent(extent, cols, rows)

          val layers = layersString.split(",")
          val weights = weightsString.split(",").map(_.toInt)
          val breaks = breaksString.split(",").map(_.toInt)

          val model = Model.weightedOverlay(layers, weights, re)
          val mask = new GeoJson(maskParam)
          val overlay = applyMask(model, mask)

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
              failWith(new RuntimeException(message))
          }
      }
    }
  }

  val histogramRoute = path("gt" / "histogram") {
    parameters('layer, 'mask ? "") {
      (layerParam, maskParam) => {
        val start = System.currentTimeMillis()

        val mask = new GeoJson(maskParam)
        val rs = RasterSource(layerParam)

        val summary = mask.toGeometry match {
          // Convert JTS geometry to GeoTrellis feature polygon.
          case Some(pMask) => rs.zonalHistogram(Polygon(pMask, 0))
          case None => rs.histogram
        }

        summary.run match {
          case process.Complete(result, h) =>
            val elapsedTotal = System.currentTimeMillis - start
            val histogram = result.toJSON
            val data =
              s"""{
                "elapsed": "$elapsedTotal",
                "histogram": $histogram
                  }"""
            complete(data)
          case process.Error(message, trace) =>
            failWith(new RuntimeException(message))
        }
      }
    }
  }

  val staticRoute = pathPrefix("") {
    getFromDirectory(ServiceConfig.staticPath)
  }

  val serviceRoute =
    indexRoute ~
    colorsRoute ~
    breaksRoute ~
    overlayRoute ~
    histogramRoute ~
    staticRoute
}

