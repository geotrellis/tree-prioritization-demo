package org.opentreemap.modeling

import geotrellis.raster._
import geotrellis.raster.io._
import geotrellis.raster.histogram.{Histogram, StreamingHistogram}
import geotrellis.raster.render._
import geotrellis.spark._
import geotrellis.spark.buffer.{BufferedTile, Direction}
import geotrellis.spark.io._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.summary._
import geotrellis.spark.tiling.FloatingLayoutScheme
import geotrellis.spark.mapalgebra._
import geotrellis.spark.mapalgebra.focal._
import geotrellis.spark.mapalgebra.focal.hillshade._
import geotrellis.proj4._
import geotrellis.raster.io.geotiff.GeoTiff
import geotrellis.raster.io.geotiff.writer.GeoTiffWriter
import geotrellis.raster.mapalgebra.focal.{Square, TargetCell}
import geotrellis.raster.mapalgebra.focal.hillshade.Hillshade
import geotrellis.raster.rasterize._
import geotrellis.raster.rasterize.polygon._
import geotrellis.raster.summary.polygonal._
import geotrellis.spark.io.AttributeStore
import geotrellis.spark.io.avro.AvroRecordCodec
import geotrellis.util._
import geotrellis.vector._
import geotrellis.vector.io._

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.actor.ActorRef
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import ch.megard.akka.http.cors.CorsDirectives._
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.JsonParser.ParsingException
import spire.syntax.cfor._

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

import ModelingTypes._


trait Router extends Directives
    with AkkaSystem.LoggerExecutor
    with TileServiceLogic
    with TileGetter
    with VectorHandling
    with LayerMasking
    with TileLayerMasking { self: ReaderSet =>

  import AkkaSystem.materializer

  implicit def rejectionHandler =
    RejectionHandler.newBuilder().handleAll[MethodRejection] { rejections =>
      val methods = rejections map (_.supported)
      lazy val names = methods map (_.name) mkString ", "

      respondWithHeader(Allow(methods)) {
        options {
          complete(s"Supported methods : $names.")
        } ~
        complete(MethodNotAllowed, s"HTTP method not allowed, supported methods: $names!")
      }
    }
    .result()

  def printAnyException[T](f: => T): T= {
    try {
      f
    } catch {
      case e: Throwable =>
        import java.io._
        val sw = new StringWriter
        e.printStackTrace(new PrintWriter(sw))
        println(sw.toString)
        throw e
    }
  }

  def computeBreaks(bbox: String, layersParam: String, weightsParam: String, numBreaks: Int,
                    polyMaskParam: String, layerMaskParam: String): String = {
    val extent = ProjectedExtent(Extent.fromString(bbox), LatLng).reproject(WebMercator)
    // TODO: Dynamic breaks based on configurable breaks resolution.

    val layers = layersParam.split(",")
    val weights = weightsParam.split(",").map(_.toInt)

    val parsedLayerMask = try {
      import spray.json.DefaultJsonProtocol._
      Some(layerMaskParam.parseJson.convertTo[LayerMaskType])
    } catch {
      case ex: ParsingException =>
        if (!layerMaskParam.isEmpty)
          ex.printStackTrace(Console.err)
        None
    }

    val polys = reprojectPolygons(
      parsePolyMaskParam(polyMaskParam),
      4326
    )

    clipExtentToExtentOfPolygons(extent, polys) match {
      case None => s"""{ "error" : "Polygon masks do not intersect map bounds."}"""
      case Some(extentClipped) => {
        val unmasked = weightedOverlayForBreaks(layers, weights, extent, extentClipped)
        val masked = applyMasks(
          unmasked,
          polyMask(polys),
          layerMask(getMasksFromCatalog(parsedLayerMask, extentClipped, breaksZoom))
        )
        val breaks = masked.classBreaksExactInt(numBreaks)
        if (breaks.size > 0 && breaks(0) == NODATA) {
          s"""{ "error" : "Unable to calculate breaks (NODATA)."} """
        } else {
          val breaksArray = breaks.mkString("[", ",", "]")
          s"""{ "classBreaks" : $breaksArray }"""
        }
      }
    }
  }

  def respondWithImage(bytes: Array[Byte]) =
    Some(HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), bytes)))

  def respondWithJson(s: String) =
    Some(HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`application/json`), s)))

  def routes =
    pathPrefix("gt" / "health-check") {
      get {
        complete {
          Future {
            val result =
              attributeStore.layerIds
            if (!result.isEmpty) {
              "OK"
            } else {
              throw new RuntimeException("Health check: layers did not list from catalog'")
            }
          }
        }
      }
    } ~
    path("gt" / "breaks") {
      options {
        cors() {
          complete("OK")
        }
      } ~
      get {
        parameters('bbox,
                   'layers,
                   'weights,
                   'numBreaks.as[Int],
                   'threshold.as[Int] ? NODATA,
                   'polyMask ? "",
                   'layerMask ? "") {
          (bbox, layersParam, weightsParam, numBreaks, threshold,
              polyMaskParam, layerMaskParam) => {
            cors() {
                complete {
                  Future {
                    respondWithJson {
                      computeBreaks(bbox, layersParam, weightsParam, numBreaks, polyMaskParam, layerMaskParam)
                    }
                  }
                }
            }
          }
        }
      }
    } ~
    path("gt" / "tile"/ IntNumber / IntNumber / IntNumber ~ ".png" ) { (z, x, y) =>
      options {
        cors() {
          complete("OK")
        }
      } ~
      get {
        parameters('bbox,
                   'layers,
                   'weights,
                   'palette ? "ff0000,ffff00,00ff00,0000ff",
                   'breaks,
                   'colorRamp ? "blue-to-red",
                   'threshold.as[Int] ? NODATA,
                   'polyMask ? "",
                   'layerMask ? "") {
          (bbox, layersString, weightsString,
           palette, breaksString, colorRamp, threshold,
           polyMaskParam, layerMaskParam) => {
                complete {
                  Future {
                    val extent = ProjectedExtent(Extent.fromString(bbox), LatLng).reproject(WebMercator)
                    val layers = layersString.split(",")

                    val weights = weightsString.split(",").map(_.toInt)
                    val breaks = breaksString.split(",").map(_.toInt)

                    val parsedLayerMask = try {
                      import spray.json.DefaultJsonProtocol._
                      Some(layerMaskParam.parseJson.convertTo[LayerMaskType])
                    } catch {
                      case ex: ParsingException =>
                        if (!layerMaskParam.isEmpty)
                          ex.printStackTrace(Console.err)
                        None
                    }

                    val polys = reprojectPolygons(
                      parsePolyMaskParam(polyMaskParam),
                      4326
                    )

                    val unmasked = weightedOverlay(layers, weights, extent, z, x, y)
                    val masked = applyTileMasks(
                      unmasked,
                      polyTileMask(polys, z, x, y),
                      layerTileMask(getMaskTiles(parsedLayerMask, z, x, y)),
                      thresholdTileMask(threshold)
                    )

                    val tile = renderTile(masked, breaks, colorRamp)
                    respondWithImage {
                      tile.bytes
                    }
                  }
                }
          }
        }
      }
    }
}
