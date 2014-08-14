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
        if (!mask.isEmpty)
          ex.printStackTrace(Console.err)
        Seq[Polygon]()
    }
  }

  /** Convert GeoJson string to Polygon sequence.
    * The `polyMask` parameter expects a single GeoJson blob,
    * so this should never return a sequence with more than 1 element.
    * However, we still return a sequence, in case we want this param
    * to support multiple polygons in the future.
    */
  def parsePolyMaskParam(polyMask: String): Seq[Polygon] = getPolygons(polyMask)

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
                     polyMask: String,
                     layerMask: Option[LayerMaskType],
                     extent: RasterExtent): RasterSource = {
    getMaskedModel(model,
      parsePolyMaskParam(polyMask),
      parseLayerMaskParam(layerMask, extent))
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
                 polyMask: String,
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

  def histogram(model: RasterSource, polyMask: Seq[Polygon]) = {
    val summary: ValueSource[Histogram] = {
      if (polyMask.size > 0) {
        val histograms =
          DataSource.fromSources(
            polyMask map { p => model.zonalHistogram(p) }
          )
        histograms.converge { seq => FastMapHistogram.fromHistograms(seq) }
      } else {
        model.histogram
      }
    }
    summary.run
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
      formFields('bbox,
                 'layers,
                 'weights,
                 'numBreaks.as[Int],
                 'polyMask ? "",
                 'layerMask ? "") {
        (bbox, layersParam, weightsParam, numBreaks, polyMask, layerMaskParam) => {
          val extent = Extent.fromString(bbox)
          // TODO: Dynamic breaks based on configurable breaks resolution.
          val rasterExtent = RasterExtent(extent, 256, 256)

          val layers = layersParam.split(",")
          val weights = weightsParam.split(",").map(_.toInt)

          val layerMask = try {
            import spray.json.DefaultJsonProtocol._
            layerMaskParam.parseJson.convertTo[LayerMaskType]
          } catch {
            case ex: ParsingException =>
              if (!layerMaskParam.isEmpty)
                ex.printStackTrace(Console.err)
              Map[String, Array[Int]]()
          }

          val unmasked = weightedOverlay(layers, weights, rasterExtent)
          val model = getMaskedModel(unmasked, polyMask, Some(layerMask), rasterExtent)

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
            palette, breaksString, colorRamp, polyMask, layerMaskParam) => {
          val extent = Extent.fromString(bbox)
          val rasterExtent = RasterExtent(extent, cols, rows)

          val layers = layersString.split(",")
          val weights = weightsString.split(",").map(_.toInt)
          val breaks = breaksString.split(",").map(_.toInt)

          val layerMask = try {
            import spray.json.DefaultJsonProtocol._
            Some(layerMaskParam.parseJson.convertTo[LayerMaskType])
          } catch {
            case ex: ParsingException =>
              if (!layerMaskParam.isEmpty)
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
      formFields('bbox,
                 'layer,
                 'polyMask ? "") {
        (bbox, layer, polyMaskParam) => {
          val start = System.currentTimeMillis()
          val extent = Extent.fromString(bbox)
          // TODO: Dynamic breaks based on configurable breaks resolution.
          val rasterExtent = RasterExtent(extent, 256, 256)
          val rs = createRasterSource(layer, rasterExtent)
          val polyMask = parsePolyMaskParam(polyMaskParam)
          val summary = histogram(rs, polyMask)
          summary match {
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

