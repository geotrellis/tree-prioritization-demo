package org.opentreemap.modeling

import scala.concurrent._

import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.render._
import geotrellis.raster.resample._
import geotrellis.raster.op.stats._
import geotrellis.services._
import geotrellis.vector._
import geotrellis.vector.io.json._
import geotrellis.vector.reproject._
import geotrellis.proj4._
import geotrellis.spark._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.s3._
import geotrellis.spark.tiling._
import geotrellis.spark.utils._
import geotrellis.spark.op.zonal.summary._
import geotrellis.spark.op.local._
import geotrellis.spark.op.stats._

import org.apache.spark._
import org.apache.hadoop.fs._

import akka.actor.Actor
import spray.routing.HttpService
import spray.routing.ExceptionHandler
import spray.http.MediaTypes
import spray.http.StatusCodes

import spray.json._
import spray.json.JsonParser.ParsingException

import com.vividsolutions.jts.{ geom => jts }

class ModelingServiceSparkActor extends Actor with ModelingServiceSpark {
  override def actorRefFactory = context
  override def receive = runRoute(serviceRoute)
}

trait ModelingServiceSparkLogic {
  import ModelingTypes._


  /** Multiply `layers` by their corresponding `weights` and combine
    * them to form a single raster.
    */


  /** Return a class breaks operation for `model`. */
  def getBreaks(model: RasterRDD[SpatialKey], numBreaks: Int) = {
   model.classBreaks(numBreaks)
  }

  /** Return a render tile as PNG operation for `model`. */
  def renderTile(model: RasterRDD[SpatialKey], breaks: Seq[Int], colorRamp: String): Png = {
    val ramp = {
      val cr = ColorRampMap.getOrElse(colorRamp, ColorRamps.BlueToRed)
      cr.interpolate(breaks.length)
    }
    model.stitch.renderPng(ramp.toArray, breaks.toArray)
  }


  /** Return raster value distribution for `model`. */
  def histogram(rdd: RasterRDD[SpatialKey], polyMask: Seq[Polygon]): Histogram = {
    if (polyMask.size > 0) {
      val histograms: Seq[Histogram] = polyMask map {
        p => {
          println(s"POLYGON: $p")
          rdd.zonalHistogram(p)
        }
      }
      println(s"HISTOGRAMS: $histograms")
      FastMapHistogram.fromHistograms(histograms)
    } else {
      null
    }
  }

  /** Return raster values for a sequence of points. */
  def rasterValues(tileReader: SpatialKey => Tile, metadata: RasterMetaData)(points: Seq[(String, Point)]) : Seq[(String, Point, Int)] = {
    val keyedPoints: Map[SpatialKey, Seq[(String, Point)]] =
      points
        .map { case(id, p) =>
          // TODO, don't project if the raster is already in WebMercator
          metadata.mapTransform(p.reproject(WebMercator, metadata.crs)) -> (id, p)
        }
        .groupBy(_._1)
        .map { case(k, pairs) => k -> pairs.map(_._2) }

    keyedPoints.map { case(k, points) =>
      val raster = Raster(tileReader(k), metadata.mapTransform(k))
      points.map { case(id, p) =>
        // TODO, don't project if the raster is already in WebMercator
        val projP = p.reproject(WebMercator, metadata.crs)
        val (col, row) = raster.rasterExtent.mapToGrid(projP.x, projP.y)
        (id, p, raster.tile.get(col,row))
      }
    }.toSeq.flatten
  }


}

trait ModelingServiceSpark extends HttpService with ModelingServiceSparkLogic {
  import ModelingTypes._

  implicit def executionContext = actorRefFactory.dispatcher

  implicit val sparkContext = SparkUtils.createSparkContext("OTM Modeling Context", new SparkConf())

  lazy val serviceRoute =
    handleExceptions(exceptionHandler) {
      breaksRoute ~
      histogramRoute ~
      rasterValueRoute ~
      overlayTmsRoute
    }

  /** Handle all exceptions that bubble up from `failWith` calls. */






  lazy val histogramRoute = path("gt" / "spark"/ "histogram") {
    post {
      formFields('layer,
                 'srid.as[Int],
                 'polyMask ? "") {
        (layer, srid, polyMaskParam) => {
          val start = System.currentTimeMillis()
          val layerId = (layer, ModelingServiceSparkActor.DEFAULT_ZOOM)
          val metadata: RasterMetaData = ModelingServiceSparkActor.catalog.getLayerMetadata(layerId).rasterMetaData


          // val layerMetaData = ModelingServiceSparkActor.catalog.attributeStore.read[S3LayerMetaData](layerId, "metadata")
          // val metadata = layerMetaData.rasterMetaData


          // TODO: dont double reproject
          val webMPolys = reprojectPolygons(
            parsePolyMaskParam(polyMaskParam),
            srid
          )
          val polys = webMPolys map { p => p.reproject(WebMercator, metadata.crs) }

          // TODO expand extent to include multiple polygons
          val polygonsExtent = polys.head.envelope

          println(s"POLYGONSEXTENT: $polygonsExtent")

          val baseQuery = ModelingServiceSparkActor.catalog.query[SpatialKey]((layer, ModelingServiceSparkActor.DEFAULT_ZOOM))
          val intersection = baseQuery.where(Intersects(polygonsExtent))
          val rdd = intersection.toRDD

          val result = histogram(rdd, polys)
          val elapsedTotal = System.currentTimeMillis - start
          val histogramJson = result.toJSON
          val data =
            s"""{
                  "elapsed": "$elapsedTotal",
                  "histogram": $histogramJson
                    }"""
          complete(data)
        }
      }
    }
  }

  lazy val rasterValueRoute = path("gt" / "spark" / "value") {
    post {
      formFields('layer, 'coords, 'srid.as[Int]) {
        (layer, coordsParam, srid) =>

        val layerId = (layer, ModelingServiceSparkActor.DEFAULT_ZOOM)
        println(s"LAYERID:$layerId")
        val rasterMetaData = ModelingServiceSparkActor.catalog.getLayerMetadata(layerId).rasterMetaData
        println(s"RASTERMETADATA:$rasterMetaData")
        val tileReader = ModelingServiceSparkActor.catalog.tileReader[SpatialKey](layerId)
        println(s"TILEREADER:$tileReader")

        val points = coordsParam.split(",").grouped(3).map {
          // TODO: Preserve ids through the processing
          case Array(id, xParam, yParam) =>
            try {
              val pt = reprojectPoint(
                Point(xParam.toDouble, yParam.toDouble),
                srid
              )
              Some((id, pt))
            } catch {
              case ex: NumberFormatException => None
            }
        }.toList.flatten

        val values = rasterValues(tileReader, rasterMetaData)(points)

        import spray.json.DefaultJsonProtocol._
        // TODO: Return points with values
        complete(s"""{ "coords": ${values.toJson} }""")
      }
    }
  }
}
