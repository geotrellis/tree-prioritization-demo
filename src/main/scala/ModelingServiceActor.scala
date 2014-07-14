package org.opentreemap.modeling

import geotrellis.process
import geotrellis.Extent
import geotrellis.RasterExtent
import geotrellis.Png
import geotrellis.source.ValueSource
import geotrellis.source.RasterSource
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

class ModelingServiceActor() extends Actor with ModelingService {
  def actorRefFactory = context
  def receive = runRoute(serviceRoute)
}

trait ModelingService extends HttpService {
  implicit def executionContext = actorRefFactory.dispatcher

  def getMaskSource(mask: String, layerName: String, featureId: String): PolygonSource = {
    if (layerName != "" && featureId != "") {
      new FilePolygonSource(ServiceConfig.featuresPath)
    } else {
      new StringPolygonSource(mask)
    }
  }

  def maskOverlay(mask: Option[GeoJson], model: RasterSource): RasterSource = {
    mask match {
      case Some(m) => m.toGeometry match {
        case Some(p: jts.Polygon) => model.mask(p)
        case Some(p: jts.MultiPolygon) => model.mask(p)
        case _ => model
      }
      case None => throw new Exception("Feature mask not found.")
    }
  }

  val indexRoute = pathSingleSlash {
    getFromFile(ServiceConfig.staticPath + "/index.html")
  }

  val maskRoute = path("mask") {
    parameters('layerName, 'featureId) {
      (layerName, featureId) => {
        val source = getMaskSource("", layerName, featureId)
        source.getGeoJson(layerName, featureId) match {
          case Some(shape) => complete(shape.toString)
          case None => failWith(new Exception("Feature mask not found."))
        }
      }
    }
  }

  val colorsRoute = path("gt" / "colors") {
    complete(ColorRampMap.getJson)
  }

  val breaksRoute = path("gt" / "breaks") {
    parameters('layers,
               'weights,
               'numBreaks.as[Int],
               'mask ? "",
               'layerName ? "",
               'featureId ? "") {
      (layersParam, weightsParam, numBreaks, maskParam, layerName, featureId) => {
        val extent = Extent(-19840702.0356, 2143556.8396, -7452702.0356, 11537556.8396)
        val re = RasterExtent(extent, 256, 256)

        val layers = layersParam.split(",")
        val weights = weightsParam.split(",").map(_.toInt)

        val model = Model.weightedOverlay(layers, weights, re)
        val maskSource = getMaskSource(maskParam, layerName, featureId)
        val geoJson = maskSource.getGeoJson(layerName, featureId)
        val overlay = maskOverlay(geoJson, model)

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
               'colors.as[Int] ? 4,
               'breaks,
               'colorRamp ? "blue-to-red",
               'mask ? "",
               'layerName ? "",
               'featureId ? "") {
        (_, _, _, _, bbox, cols, rows, layersString, weightsString,
          palette, colors, breaksString, colorRamp, maskParam, layerName, featureId) => {
          val extent = Extent.fromString(bbox)
          val re = RasterExtent(extent, cols, rows)

          val layers = layersString.split(",")
          val weights = weightsString.split(",").map(_.toInt)
          val breaks = breaksString.split(",").map(_.toInt)

          val model = Model.weightedOverlay(layers, weights, re)
          val maskSource = getMaskSource(maskParam, layerName, featureId)
          val geoJson = maskSource.getGeoJson(layerName, featureId)
          val overlay = maskOverlay(geoJson, model)

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

  val sumRoute = path("gt" / "sum") {
    parameters('polygon, 'layers, 'weights) {
      (polygonJson, layersString, weightsString) => {
        val start = System.currentTimeMillis()

        val layers = layersString.split(",")
        val weights = weightsString.split(",").map(_.toInt)

        var poly = new GeoJson(polygonJson)
        val summary = poly.toGeometry match {
          case Some(m) => Model.summary(layers, weights, m)
          case None => throw new Exception("Polygon required.")
        }

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

  val staticRoute = pathPrefix("") {
    getFromDirectory(ServiceConfig.staticPath)
  }

  val serviceRoute =
    indexRoute ~
    maskRoute ~
    colorsRoute ~
    breaksRoute ~
    overlayRoute ~
    sumRoute ~
    staticRoute
}

