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

import akka.actor.Actor
import spray.routing.HttpService
import spray.http.MediaTypes

import spray.json._
import org.parboiled.errors.ParsingException

import com.vividsolutions.jts.{ geom => jts }


object ModelingTypes {
  // Array of GeoJSON strings.
  type PolyMaskType = Array[String]

  // Map of layer names to selected values.
  // Ex. { Layer1: [1, 2, 3], ... }
  type LayerMaskType = Map[String, Array[Int]]
}


class ModelingServiceActor extends Actor with ModelingService {
  override def actorRefFactory = context
  override def receive = runRoute(serviceRoute)
}


trait ModelingServiceLogic {
  def createRasterSource(layer: String, extent: RasterExtent) =
    RasterSource(layer, extent)

  import ModelingTypes._

  def getPolygons(mask: String): Seq[Polygon] = {
    import spray.json.DefaultJsonProtocol._
    try {
      mask
        .parseGeoJson[JsonFeatureCollection]
        .getAllPolygons
        .map(_.reproject(LatLng, WebMercator))
    } catch {
      case ex: ParsingException =>
        ex.printStackTrace(Console.err)
        Seq[Polygon]()
    }
  }

  /** Convert array of strings to a Polygon sequence. */
  def parsePolyMaskParam(polyMask: Option[PolyMaskType]): Iterable[Polygon] = {
    polyMask match {
      case Some(masks: PolyMaskType) =>
        // Store result in a variable before returning to prevent some kind
        // of reflection error during compilation.
        val result = masks.map(getPolygons(_)).filter(_.length > 0).flatten
        result
      case None =>
        Seq[Polygon]()
    }
  }

  /** Convert layer mask map to a RasterSource sequence.  */
  def parseLayerMaskParam(layerMask: Option[LayerMaskType],
                          extent: RasterExtent): Iterable[RasterSource] = {
    layerMask match {
      case Some(masks: LayerMaskType) =>
        masks map { case (layerName, values) =>
          createRasterSource(layerName, extent).localMap { z =>
            if (values contains z) z
            else NODATA
          }
        }
      case None =>
        Seq[RasterSource]()
    }
  }

  def getMaskedModel(model: RasterSource,
                     polyMask: Option[PolyMaskType],
                     layerMask: Option[LayerMaskType],
                     extent: RasterExtent): RasterSource = {
    getMaskedModel(model, parsePolyMaskParam(polyMask), parseLayerMaskParam(layerMask, extent))
  }

  def getMaskedModel(model: RasterSource,
                     polyMasks: Iterable[Polygon],
                     layerMasks: Iterable[RasterSource]): RasterSource = {
    // Polygon masks.
    val polyMasked =
      if (polyMasks.size > 0)
        model.mask(polyMasks)
      else
        model

    // Raster layer mask.
    val layerMasked =
      if (layerMasks.size > 0) {
        layerMasks.foldLeft(model) { (rs, mask) =>
          rs.localCombine(mask) { (z, maskValue) =>
            if (isData(maskValue)) z
            else NODATA
          }
        }
      } else {
        model
      }

    layerMasked.localCombine(polyMasked) { (z, value) =>
      if (isData(value)) z
      else NODATA
    }
  }

  def weightedOverlay(layers:Seq[String],
                      weights:Seq[Int],
                      rasterExtent: RasterExtent): RasterSource = {
    layers
      .zip(weights)
      .map { case (layer, weight) =>
        createRasterSource(layer, rasterExtent)
          .convert(TypeByte)
          .localMultiply(weight)
       }
      .localAdd
  }

  def getBreaks(model: RasterSource, numBreaks: Int) = {
   model.classBreaks(numBreaks).run
  }

  def renderTile(layers: Seq[String],
                 weights: Seq[Int],
                 breaks: Seq[Int],
                 rasterExtent: RasterExtent,
                 colorRamp: String,
                 polyMask: Option[PolyMaskType],
                 layerMask: Option[LayerMaskType]) = {
    val unmasked = weightedOverlay(layers, weights, rasterExtent)
    val model = getMaskedModel(unmasked, polyMask, layerMask, rasterExtent)

    val ramp = {
      val cr = ColorRampMap.getOrElse(colorRamp, ColorRamps.BlueToRed)
      cr.interpolate(breaks.length)
    }

    val png: ValueSource[Png] = model.renderPng(ramp.toArray, breaks.toArray)
    png.run
  }
}

trait ModelingService extends HttpService with ModelingServiceLogic {
  implicit def executionContext = actorRefFactory.dispatcher

  import ModelingTypes._

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

  lazy val staticRoute = pathPrefix("") {
    getFromDirectory(ServiceConfig.staticPath)
  }

  lazy val colorsRoute = path("gt" / "colors") {
    complete(ColorRampMap.getJson)
  }

  lazy val breaksRoute = path("gt" / "breaks") {
    post {
      formFields('layers,
                 'weights,
                 'numBreaks.as[Int],
                 'polyMask ? "",
                 'layerMask ? "") {
        (layersParam, weightsParam, numBreaks, polyMaskParam, layerMaskParam) => {
          // TODO: Read extent from query string (bbox).
          val extent = Extent(-19840702.0356, 2143556.8396, -7452702.0356, 11537556.8396)
          // TODO: Dynamic breaks based on configurable breaks resolution.
          val rasterExtent = RasterExtent(extent, 256, 256)

          val layers = layersParam.split(",")
          val weights = weightsParam.split(",").map(_.toInt)

          val polyMask = try {
            import spray.json.DefaultJsonProtocol._
            polyMaskParam.parseJson.convertTo[PolyMaskType]
          } catch {
            case ex: ParsingException =>
              ex.printStackTrace(Console.err)
              Array[String]()
          }

          val layerMask = try {
            import spray.json.DefaultJsonProtocol._
            layerMaskParam.parseJson.convertTo[LayerMaskType]
          } catch {
            case ex: ParsingException =>
              ex.printStackTrace(Console.err)
              Map[String, Array[Int]]()
          }

          val unmasked = weightedOverlay(layers, weights, rasterExtent)
          val model = getMaskedModel(unmasked, Some(polyMask), Some(layerMask), rasterExtent)

          val breaksResult = getBreaks(model, numBreaks)
          breaksResult match {
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
  }

  lazy val overlayRoute = path("gt" / "wo") {
    post {
      formFields('service,
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
                 'polyMask ? "",
                 'layerMask ? "") {
        (_, _, _, _, bbox, cols, rows, layersString, weightsString,
            palette, breaksString, colorRamp, polyMaskParam, layerMaskParam) => {
          val extent = Extent.fromString(bbox)
          val rasterExtent = RasterExtent(extent, cols, rows)

          val layers = layersString.split(",")
          val weights = weightsString.split(",").map(_.toInt)
          val breaks = breaksString.split(",").map(_.toInt)

          val polyMask = try {
            import spray.json.DefaultJsonProtocol._
            Some(polyMaskParam.parseJson.convertTo[PolyMaskType])
          } catch {
            case ex: ParsingException =>
              ex.printStackTrace(Console.err)
              None
          }

          val layerMask = try {
            import spray.json.DefaultJsonProtocol._
            Some(layerMaskParam.parseJson.convertTo[LayerMaskType])
          } catch {
            case ex: ParsingException =>
              ex.printStackTrace(Console.err)
              None
          }

          val tileResult = renderTile(layers, weights, breaks, rasterExtent, colorRamp,
            polyMask, layerMask)

          tileResult match {
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
  }

  lazy val histogramRoute = path("gt" / "histogram") {
    post {
      formFields('layer, 'polyMask ? "") {
        (layerParam, polyMask) => {
          val start = System.currentTimeMillis()

          // TODO: Read extent from query string (bbox).
          val extent = Extent(-19840702.0356, 2143556.8396, -7452702.0356, 11537556.8396)
          // TODO: Dynamic breaks based on configurable breaks resolution.
          val rasterExtent = RasterExtent(extent, 256, 256)

          val rs = createRasterSource(layerParam, rasterExtent)

          val summary: ValueSource[Histogram] = {
            val maskPolygons = getPolygons(polyMask)
            if (maskPolygons.length > 0) {
              val histograms =
                DataSource.fromSources(
                  maskPolygons.map { p => rs.zonalHistogram(p) }
                )
              histograms.converge { seq => FastMapHistogram.fromHistograms(seq) }
            } else {
              rs.histogram
            }
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
  }
}

