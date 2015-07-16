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

object ModelingServiceSparkActor {

  val DEFAULT_ZOOM = 13

  def catalog(implicit sc: SparkContext): S3RasterCatalog = {
    S3RasterCatalog("com.azavea.datahub", "catalog")
  }

  def metaDatas(implicit sc: SparkContext) = {
    val attributeStore = catalog.attributeStore
    val all = attributeStore.readAll[S3LayerMetaData]("metadata")
    println(all)
    all
  }

  def zoomLevelsFor(implicit sc: SparkContext, layerName: String) = {
    metaDatas.keys.filter(_.name == layerName).map(_.zoom).toSeq
  }

/*
  // LOCAL HADOOP CATALOG

  val DATA_PATH = "data/catalog"

  def catalogPath(implicit sc: SparkContext): Path = {
    val localFS = new Path(System.getProperty("java.io.tmpdir")).getFileSystem(sc.hadoopConfiguration)
    new Path(localFS.getWorkingDirectory, DATA_PATH +  "/hadoop")
  }

  def catalog(implicit sc: SparkContext): HadoopRasterCatalog = {
    val conf = sc.hadoopConfiguration
    val localFS = catalogPath.getFileSystem(sc.hadoopConfiguration)
    val doesNotExist = !localFS.exists(catalogPath)
    val catalog = HadoopRasterCatalog(catalogPath)

    if (doesNotExist) {
      println(s"Writing data to the catalog")
      LocalHadoopCatalog.writeTiffsToCatalog(catalog, DATA_PATH)
    }
    catalog
  }
 */
}

class ModelingServiceSparkActor extends Actor with ModelingServiceSpark {
  override def actorRefFactory = context
  override def receive = runRoute(serviceRoute)
}


trait ModelingServiceSparkLogic {
  import ModelingTypes._

  /** Convert GeoJson string to Polygon sequence.
    * The `polyMask` parameter expects a single GeoJson blob,
    * so this should never return a sequence with more than 1 element.
    * However, we still return a sequence, in case we want this param
    * to support multiple polygons in the future.
    */
  def parsePolyMaskParam(polyMask: String): Seq[Polygon] = {
    try {
      import spray.json.DefaultJsonProtocol._
      val featureColl = polyMask.parseGeoJson[JsonFeatureCollection]
      val polys = featureColl.getAllPolygons union
                  featureColl.getAllMultiPolygons.map(_.polygons).flatten
      polys
    } catch {
      case ex: ParsingException =>
        if (!polyMask.isEmpty)
          ex.printStackTrace(Console.err)
        Seq[Polygon]()
    }
  }

 /** Convert `layerMask` map to list of filtered rasters.
    * The result contains a raster for each layer specified,
    * and that raster only contains whitelisted values present
    * in the `layerMask` argument.
    */
  def parseLayerMaskParam(implicit sc:SparkContext,
                          layerMask: Option[LayerMaskType],
                          rasterExtent: RasterExtent): Iterable[RasterRDD[SpatialKey]] = {
    layerMask match {
      case Some(masks: LayerMaskType) =>
        masks map { case (layerName, values) =>
          ModelingServiceSparkActor.catalog.query[SpatialKey]((layerName, ModelingServiceSparkActor.DEFAULT_ZOOM))
          .where(Intersects(rasterExtent.extent))
          .toRDD
          .localMap { z =>
            if (values contains z) z
            else NODATA
          }

        }
      case None =>
        Seq[RasterRDD[SpatialKey]]()
    }
  }

  def parseLayerTileMaskParam(implicit sc:SparkContext,
                              layerMask: Option[LayerMaskType],
                              z:Int, x:Int, y:Int): Iterable[Tile] = {
    layerMask match {
      case Some(masks: LayerMaskType) =>
        masks map { case (layer, values) =>
          val reader = ModelingServiceSparkActor.catalog.tileReader[SpatialKey]((layer, z))
          val tile = reader(SpatialKey(x, y))
          tile.map { z =>
            if (values contains z) z
            else NODATA
          }

        }
      case None =>
        Seq[Tile]()
    }
  }

  /** Combine multiple polygons into a single mask raster. */
  def polyMask(polyMasks: Iterable[Polygon])(model: RasterRDD[SpatialKey]): RasterRDD[SpatialKey] = {
    // TODO: Pull in an updated Geotrellis when this is complete and merged
    // https://github.com/zifeo/geotrellis/commit/d63608cb7d77c0358a5dd8118f6289d6d9366799
    model
  }

  def polyTileMask(polyMasks: Iterable[Polygon])(tile: Tile): Tile = {
    // TODO: Pull in an updated Geotrellis when this is complete and merged
    // https://github.com/zifeo/geotrellis/commit/d63608cb7d77c0358a5dd8118f6289d6d9366799
    tile
  }

  /** Combine multiple rasters into a single raster.
    * The resulting raster will contain values from `model` only at
    * points that have values in every `layerMask`. (AND-like operation)
    */
  def layerMask(layerMasks: Iterable[RasterRDD[SpatialKey]])(model: RasterRDD[SpatialKey]): RasterRDD[SpatialKey] = {
    if (layerMasks.size > 0) {
      layerMasks.foldLeft(model) { (rdd, mask) =>
        rdd.combineTiles(mask) { (rddTile, maskTile) =>
          rddTile.combine(maskTile) { (z, maskValue) =>
            if (isData(maskValue)) z
            else NODATA
          }
        }
      }
    } else {
      model
    }
  }

  def layerTileMask(layerMasks: Iterable[Tile])(tile: Tile): Tile = {
    if (layerMasks.size > 0) {
      layerMasks.foldLeft(tile) { (acc, mask) =>
        acc.combine(mask) { (z, maskValue) =>
          if (isData(maskValue)) z
          else NODATA
        }
      }
    } else {
      tile
    }
  }

  /** Filter all values from `model` that are less than `threshold`. */
  def thresholdMask(threshold: Int)(model: RasterRDD[SpatialKey]): RasterRDD[SpatialKey] = {
    if (threshold > NODATA) {
      model.localMap { z =>
        if (z >= threshold) z
        else NODATA
      }
    } else {
      model
    }
  }

  def thresholdTileMask(threshold: Int)(tile: Tile): Tile = {
    if (threshold > NODATA) {
      tile.map { z =>
        if (z >= threshold) z
        else NODATA
      }
    } else {
      tile
    }
  }

  /** Filter model by 1 or more masks. */
  def applyMasks(model: RasterRDD[SpatialKey], masks: (RasterRDD[SpatialKey]) => RasterRDD[SpatialKey]*) = {
    masks.foldLeft(model) { (rdd, mask) =>
      mask(rdd)
    }
  }

  def applyTileMasks(tile: Tile, masks: (Tile) => Tile*) = {
    masks.foldLeft(tile) { (acc, mask) =>
      mask(acc)
    }
  }

  /** Multiply `layers` by their corresponding `weights` and combine
    * them to form a single raster.
    */
  def weightedOverlay(implicit sc: SparkContext,
                      layers:Seq[String],
                      weights:Seq[Int],
                      bounds:Extent): RasterRDD[SpatialKey] = {
    layers
      .zip(weights)
      .map { case (layer, weight) =>
        val base = ModelingServiceSparkActor.catalog.query[SpatialKey]((layer, ModelingServiceSparkActor.DEFAULT_ZOOM))
        val intersected = base.where(Intersects(bounds))
        val rdd = intersected.toRDD
        rdd.convert(TypeByte).localMultiply(weight)
      }
      .localAdd
  }

  def addTiles(t1: Tile, t2: Tile): Tile = {
    t1.combine(t2)(_ + _)
  }

  def weightedOverlayTms(implicit sc: SparkContext,
                         layers:Seq[String],
                         weights:Seq[Int],
                         z:Int,
                         x:Int,
                         y:Int): Tile = {
    layers
      .zip(weights)
      .map { case (layer, weight) =>
        // TODO: Handle layers with different pyramids instead of using
        // DEFAULT_ZOOM
        val tile =
          if (z <= ModelingServiceSparkActor.DEFAULT_ZOOM) {
            val reader = ModelingServiceSparkActor.catalog.tileReader[SpatialKey]((layer, z))
            reader(SpatialKey(x, y))
          } else {
            val layerId = LayerId(layer, ModelingServiceSparkActor.DEFAULT_ZOOM)
            val reader = ModelingServiceSparkActor.catalog.tileReader[SpatialKey](layerId)
            val rmd = ModelingServiceSparkActor.catalog.getLayerMetadata(layerId).rasterMetaData
            val layoutLevel = ZoomedLayoutScheme().levelFor(z)
            val mapTransform = MapKeyTransform(rmd.crs, layoutLevel.tileLayout.layoutCols, layoutLevel.tileLayout.layoutRows)
            val targetExtent = mapTransform(x, y)
            val gb @ GridBounds(nx, ny, _, _) = rmd.mapTransform(targetExtent)
            val sourceExtent = rmd.mapTransform(nx, ny)

            val largerTile = reader(SpatialKey(nx, ny))
            largerTile.resample(sourceExtent, RasterExtent(targetExtent, 256, 256))
          }
        tile.convert(TypeByte).map(_ * weight)
      }
      .reduce(addTiles)
  }

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

  def renderTile(tile: Tile, breaks: Seq[Int], colorRamp: String): Png = {
    val ramp = {
      val cr = ColorRampMap.getOrElse(colorRamp, ColorRamps.BlueToRed)
      cr.interpolate(breaks.length)
    }
    tile.renderPng(ramp.toArray, breaks.toArray)
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

  def reprojectPolygons(polys: Seq[Polygon], srid: Int): Seq[Polygon] = {
    srid match {
      case 3857 => polys
      case 4326 => polys.map(_.reproject(LatLng, WebMercator))
      case _ => throw new ModelingException("SRID not supported.")
    }
  }

  def reprojectPoint(point: Point, srid: Int): Point = {
    srid match {
      case 3857 => point
      case 4326 => point.reproject(LatLng, WebMercator)
      case _ => throw new ModelingException("SRID not supported.")
    }
  }
}

trait ModelingServiceSpark extends HttpService with ModelingServiceSparkLogic {
  import ModelingTypes._

  implicit def executionContext = actorRefFactory.dispatcher

  implicit val sparkContext = SparkUtils.createSparkContext("OTM Modeling Context", new SparkConf())

  lazy val serviceRoute =
    handleExceptions(exceptionHandler) {
      breaksRoute ~
      overlayRoute ~
      histogramRoute ~
      rasterValueRoute ~
      overlayTmsRoute
    }

  /** Handle all exceptions that bubble up from `failWith` calls. */
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

  lazy val breaksRoute = path("gt" / "spark" / "breaks") {
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
                val rasterExtent = RasterExtent(extent, 256, 256)

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

                val unmasked = weightedOverlay(implicitly, layers, weights, rasterExtent.extent)
                val model = applyMasks(
                  unmasked,
                  polyMask(polys),
                  layerMask(parseLayerMaskParam(implicitly, parsedLayerMask, rasterExtent)),
                  thresholdMask(threshold)
                )

                val breaks = getBreaks(model, numBreaks)
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

  lazy val overlayRoute = path("gt" / "spark" / "wo") {
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
                 'srid.as[Int],
                 'colorRamp ? "blue-to-red",
                 'threshold.as[Int] ? NODATA,
                 'polyMask ? "",
                 'layerMask ? "") {
        (_, _, _, _, bbox, cols, rows, layersString, weightsString,
            palette, breaksString, srid, colorRamp, threshold,
            polyMaskParam, layerMaskParam) => {
          val extent = Extent.fromString(bbox)
          val rasterExtent = RasterExtent(extent, cols, rows)

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

          val unmasked = weightedOverlay(implicitly, layers, weights, rasterExtent.extent)
          val model = applyMasks(
            unmasked,
            polyMask(polys),
            layerMask(parseLayerMaskParam(implicitly, parsedLayerMask, rasterExtent)),
            thresholdMask(threshold)
          )

          val tile = renderTile(model, breaks, colorRamp)
          respondWithMediaType(MediaTypes.`image/png`) {
            complete(tile.bytes)
          }
        }
      }
    }
  }

  lazy val overlayTmsRoute = path("gt" / "spark" / "wotms"/ IntNumber / IntNumber / IntNumber ~ ".png" ) { (z, x, y) =>
    post {
      formFields('layers,
                 'weights,
                 'palette ? "ff0000,ffff00,00ff00,0000ff",
                 'breaks,
                 'srid.as[Int],
                 'colorRamp ? "blue-to-red",
                 'threshold.as[Int] ? NODATA,
                 'polyMask ? "",
                 'layerMask ? "") {
        (layersString, weightsString,
         palette, breaksString, srid, colorRamp, threshold,
         polyMaskParam, layerMaskParam) => {
          respondWithMediaType(MediaTypes.`image/png`) {
            complete {
              future {
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

                val unmasked = weightedOverlayTms(implicitly, layers, weights, z, x, y)
                val masked = applyTileMasks(
                  unmasked,
                  polyTileMask(polys),
                  layerTileMask(parseLayerTileMaskParam(implicitly, parsedLayerMask, z, x, y)),
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
