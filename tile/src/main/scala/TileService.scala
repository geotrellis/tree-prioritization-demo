package org.opentreemap.modeling

import geotrellis.raster._
import geotrellis.spark.{SpatialKey, TileLayerMetadata}
import geotrellis.spark.util.SparkUtils
import geotrellis.vector._
import org.apache.spark._

import scala.concurrent._
import spray.http.MediaTypes
import spray.http.StatusCodes
import spray.json._
import spray.json.JsonParser.ParsingException
import spray.routing.HttpService
import spray.routing.ExceptionHandler

trait TileService extends HttpService
                     with TileServiceLogic
                     with VectorHandling
                     with S3CatalogReading
                     with LayerMasking
                     with TileLayerMasking {
  import ModelingTypes._

  implicit def executionContext = actorRefFactory.dispatcher

  implicit val sparkContext = SparkUtils.createSparkContext("OTM Modeling Tile Service Context", new SparkConf())

  lazy val serviceRoute =
    handleExceptions(exceptionHandler) {
      healthCheckRoute ~
      breaksRoute ~
      weightedOverlayTileRoute
    }

  lazy val exceptionHandler = ExceptionHandler {
    case ex: ModelingException =>
      ex.printStackTrace(Console.err)
      complete(StatusCodes.InternalServerError, s"""{
          "status": "${StatusCodes.InternalServerError}",
          "statusCode": ${StatusCodes.InternalServerError.intValue},
          "message": "${ex.getMessage.replace("\"", "\\\"")}"
        } """)
    case ex =>
      ex.printStackTrace(Console.err)
      complete(StatusCodes.InternalServerError)
  }

  lazy val healthCheckRoute = path("gt" / "health-check") {
    get {
      try {
        if (catalog.attributeStore.layerIds.nonEmpty) {
          complete("OK")
        } else {
          println("Attribute store contains no layer IDs")
          complete(StatusCodes.ServiceUnavailable)
        }
      } catch {
        case ex: Exception => {
          println("Health check exception: " + ex.getMessage)
          complete(StatusCodes.InternalServerError)
        }
      }
    }
  }

  lazy val breaksRoute = path("gt" / "breaks") {
    post {
      formFields('bbox,
                 'layers,
                 'weights,
                 'numBreaks.as[Int],
                 'srid.as[Int],
                 'threshold.as[Int] ? NODATA,
                 'polyMask ? "",
                 'layerMask ? "") {
        (bbox, layersParam, weightsParam, numBreaks, srid, threshold,
            polyMaskParam, layerMaskParam) => {
          respondWithMediaType(MediaTypes.`application/json`) {
            complete {
              future {
                val extent = Extent.fromString(bbox)
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
                  srid
                )

                val unmasked = weightedOverlayForBreaks(implicitly, catalog, layers, weights, extent)
                val masked = applyMasks(
                  unmasked,
                  polyMask(polys)
                  /*
                   TODO: Trying to use the land-use layer as a mask at
                   a lower zoom levels generated "empty collection"
                   exceptions. I suspect that the lower zoom versions
                   have interpolated values that don't match the
                   original, discrete values.
                   */
                  //layerMask(TileGetter.getMasksFromCatalog(implicitly, catalog, parsedLayerMask, extent, TileGetter.breaksZoom))
                )
                val breaks = masked.classBreaksExactInt(numBreaks)
                if (breaks.size > 0 && breaks(0) == NODATA) {
                  s"""{ "error" : "Unable to calculate breaks (NODATA)."} """ //failWith(new ModelingException("Unable to calculate breaks (NODATA)."))
                } else {
                  val breaksArray = breaks.mkString("[", ",", "]")
                  s"""{ "classBreaks" : $breaksArray }"""
                }
              }
            }
          }
        }
      }
    }
  }

  lazy val weightedOverlayTileRoute = path("gt" / "tile"/ IntNumber / IntNumber / IntNumber ~ ".png" ) { (z, x, y) =>
    post {
      formFields('bbox,
                 'layers,
                 'weights,
                 'palette ? "ff0000,ffff00,00ff00,0000ff",
                 'breaks,
                 'srid.as[Int],
                 'colorRamp ? "blue-to-red",
                 'threshold.as[Int] ? NODATA,
                 'polyMask ? "",
                 'layerMask ? "") {
        (bbox, layersString, weightsString,
         palette, breaksString, srid, colorRamp, threshold,
         polyMaskParam, layerMaskParam) => {
          respondWithMediaType(MediaTypes.`image/png`) {
            complete {
              future {
                val extent = Extent.fromString(bbox)
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
                  srid
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
