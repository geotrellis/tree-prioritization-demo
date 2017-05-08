package org.opentreemap.modeling

import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster._
import geotrellis.spark.{SpatialKey, TileLayerMetadata}
import geotrellis.spark.util.SparkUtils
import geotrellis.vector._
import org.apache.spark._
import spray.http.HttpHeaders.RawHeader

import scala.concurrent._
import spray.http.MediaTypes
import spray.http.StatusCodes
import spray.json._
import spray.json.JsonParser.ParsingException
import spray.routing.HttpService
import spray.routing.{Directive0, ExceptionHandler}

trait TileService extends HttpService
                     with TileServiceLogic
                     with VectorHandling
                     with S3CatalogReading
                     with LayerMasking
                     with TileLayerMasking
                     with Rollbar {
  import ModelingTypes._

  implicit def executionContext = actorRefFactory.dispatcher

  implicit val sparkContext = SparkUtils.createSparkContext("OTM Modeling Tile Service Context", new SparkConf())

  sparkContext.setLogLevel("ERROR")
  postInfoToRollbar("OpenTreeMap modeling tile service is starting")

  lazy val serviceRoute =
    handleExceptions(exceptionHandler) {
      healthCheckRoute ~
      breaksRoute ~
      weightedOverlayTileRoute
    }

  lazy val exceptionHandler = ExceptionHandler {
    case ex: ModelingException =>
      logException(ex)
      complete(StatusCodes.InternalServerError, s"""{
          "status": "${StatusCodes.InternalServerError}",
          "statusCode": ${StatusCodes.InternalServerError.intValue},
          "message": "${ex.getMessage.replace("\"", "\\\"")}"
        } """)
    case ex =>
      logException(ex)
      complete(StatusCodes.InternalServerError)
  }

  private def logException(ex:Throwable): Unit = {
    ex.printStackTrace(Console.err)
    postExceptionToRollbar(ex)
  }

  lazy val healthCheckRoute = path("gt" / "health-check") {
    get {
      val bbox = "-93.62626,44.63635,-92.72795,45.27205"
      val layersParam = "us-census-population-density-30m-epsg3857"
      val weightsParam = "2"
      val numBreaks = 10
      val polyMaskParam = ""
      val layerMaskParam = ""
      val expected = "{ \"classBreaks\" : [10,20,28,36,48,68,94,130,164,198] }"
      respondWithMediaType(MediaTypes.`text/plain`) {
        complete {
          future {
            val result = computeBreaks(bbox, layersParam, weightsParam, numBreaks, polyMaskParam, layerMaskParam)
            if (result == expected) {
              "OK"
            } else {
              throw new RuntimeException("Health check: expected breaks '" + expected + "' but got '" + result + "'")
            }
          }
        }
      }
    }
  }

  lazy val breaksRoute = path("gt" / "breaks") {
    options {
      respondWithHeaders() {
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
          respondWithMediaType(MediaTypes.`application/json`) {
            respondWithHeaders() {
              complete {
                future {
                  computeBreaks(bbox, layersParam, weightsParam, numBreaks, polyMaskParam, layerMaskParam)
                }
              }
            }
          }
        }
      }
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
        val unmasked = weightedOverlayForBreaks(implicitly, catalog, layers, weights, extent, extentClipped)
        val masked = applyMasks(
          unmasked,
          polyMask(polys),
          layerMask(TileGetter.getMasksFromCatalog(implicitly, catalog, parsedLayerMask, extentClipped, TileGetter.breaksZoom))
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

  lazy val weightedOverlayTileRoute = path("gt" / "tile"/ IntNumber / IntNumber / IntNumber ~ ".png" ) { (z, x, y) =>
    options {
      respondWithHeaders() {
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
          respondWithMediaType(MediaTypes.`image/png`) {
            respondWithHeaders() {
              complete {
                future {
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

                  // Our tiles are 512x512. Requesting Leaflet's z/x/y gives a "tile does not exist" error.
                  // But requesting a zoom one level out works.
                  // Apparently Leaflet and GeoTrellis have different ideas of TMS numbering for 512x512 tiles.
                  val zoom = z - 1

                  val unmasked = weightedOverlay(implicitly, catalog, tileReader, layers, weights, extent, zoom, x, y)
                  val masked = applyTileMasks(
                    unmasked,
                    polyTileMask(polys, zoom, x, y),
                    layerTileMask(TileGetter.getMaskTiles(implicitly, catalog, tileReader, parsedLayerMask, zoom, x, y)),
                    thresholdTileMask(threshold)
                  )

                  val tile = renderTile(masked, breaks, colorRamp)
                  tile.bytes
                }
              }
            }
          }
        }
      }
    }
  }

  private def respondWithHeaders(): Directive0 = {
    respondWithHeaders (
      RawHeader ("Access-Control-Allow-Origin", "*"),
      RawHeader ("Access-Control-Allow-Methods", "GET, OPTIONS"),
      RawHeader ("Access-Control-Allow-Credentials", "true"),
      RawHeader ("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent")
    )
  }

}
