package org.opentreemap.modeling

import java.io.File
import org.apache.commons.io.FilenameUtils

import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader._
import geotrellis.raster._
import geotrellis.raster.reproject._
import geotrellis.vector._
import geotrellis.vector.reproject._
import geotrellis.spark._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.index._
import geotrellis.spark.tiling._
import geotrellis.raster.mosaic._

import geotrellis.proj4._

import org.apache.spark._
import org.apache.hadoop._

object LocalHadoopCatalog {

  def writeTiffsToCatalog(catalog: HadoopRasterCatalog, dir: String)(implicit sc: SparkContext): Seq[RasterRDD[SpatialKey]] = {
    val d = new File(dir)
    val files = d.listFiles.filter(_.isFile).filter(_.getName.endsWith(".tif"))
    files map { f =>
      writeTiffToCatalog(catalog,
                         f.getAbsolutePath,
                         FilenameUtils.getBaseName(f.getName))
    }
  }

  def writeTiffToCatalog(catalog: HadoopRasterCatalog, path: String, name: String)(implicit sc: SparkContext): RasterRDD[SpatialKey] = {
    val rdd = makeRDD(path)
    catalog.writer[SpatialKey](RowMajorKeyIndexMethod, clobber = true).write(LayerId(s"$name", 0), rdd)
    rdd
  }

  /*
   TODO: REMOVE THIS The GeoTiffReader is returning wrong CRS and
   Extent information for our primary test raster. This is a
   workaround.
   */
  def getDetails(p: String, geoTiff: SingleBandGeoTiff) = {
    val f = new File(p)
    if (f.getName.toLowerCase == "nlcd_la_wm_ext.tif")
      (WebMercator, Extent(-13212685.582, 3983811.294, -13112685.582, 4083811.294))
    else
      (geoTiff.crs, geoTiff.extent)
  }

  def getRaster(p: String): Raster = {
    println(s"READING=$p")
    val geoTiff = GeoTiffReader.readSingleBand(p)
    val details = getDetails(p, geoTiff)
    val crs = details._1
    println(s"GEOTIFF.CRS=$crs")
    val extent = details._2
    println(s"GEOTIFF.EXTENT=$extent")
    val reprojectedExtent = extent.reproject(crs, LatLng)
    val tile = geoTiff.tile.reproject(extent, crs, LatLng).resample(500, 500)

    Raster(tile, reprojectedExtent)
  }

  def makeRDD(p: String)(implicit sc: SparkContext): RasterRDD[SpatialKey] = {
    val Raster(tile, extent) = getRaster(p)
    createRasterRDD(tile, extent)
  }

  def createRasterRDD(
    tile: Tile,
    extent: Extent
  )(implicit sc: SparkContext): RasterRDD[SpatialKey] = {
    val crs = LatLng
    val worldExtent = crs.worldExtent

    // the tile layout implies a resolution of the world layer, may/will cause resampling when .merge is called
    val layoutLevel: LayoutLevel = ZoomedLayoutScheme(tileSize = 256).levelFor(worldExtent, CellSize(extent, tile.cols, tile.rows))

    println(s"Matched $extent to zoom level ${layoutLevel.zoom}")
    val tileLayout = layoutLevel.tileLayout

    // Tile layout could be specified directly (as below), but the above method mirros the ingest methodology
    // val tileLayout =
    //   TileLayout(
    //     layoutCols,
    //     layoutRows,
    //     256,
    //     256
    //   )

    // this objects knows how to translate between world tile layout,
    // tiled relative to CRS world extent boundatires and world extents
    // we assume that the output raster is in WGS 84
    val outputMapTransform = new MapKeyTransform(worldExtent, tileLayout.layoutCols, tileLayout.layoutRows)

    val metaData = RasterMetaData(
      tile.cellType,
      extent,
      crs,
      tileLayout
    )

    val tmsTiles =
      for {
        (col, row) <- outputMapTransform(extent).coords // iterate over all tiles we should have for input extent in the world layout
      } yield {
        val key = SpatialKey(col, row)
        println(s"MAKING: $key")
        val worldTileExtent = outputMapTransform(key) // this may be partially out of bounds of input tile
        val keyTile = ArrayTile.empty(tile.cellType, tileLayout.tileCols, tileLayout.tileRows)
        // tile.merge comes from `import geotrellis.raster.mosaic`
        keyTile.merge(worldTileExtent, extent, tile)
        key -> keyTile
      }

    asRasterRDD(metaData) {
      sc.parallelize(tmsTiles)
    }
  }
}
