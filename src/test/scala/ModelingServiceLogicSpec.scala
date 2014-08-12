package org.opentreemap.modeling.test

import org.opentreemap.modeling._
import org.opentreemap.modeling.ModelingTypes._

import org.scalatest._
import geotrellis.engine._
import geotrellis.engine.op.local._
import geotrellis.vector._
import geotrellis.raster._

import geotrellis.testkit.vector._

class MockModelingServiceLogic(sources: Map[String, Tile])
    extends ModelingServiceLogic {
  override def createRasterSource(layer: String, rasterExtent: RasterExtent) = {
    RasterSource(sources(layer), rasterExtent.extent)
  }
}

class ModelingServiceLogicSpec
  extends UnitSpec {

  val n = NODATA

  // All test rasters are 1 tile with 5 x 5 cells.
  val createTileRaster = (arr: Array[Int]) => {
    val tileCols = 1
    val tileRows = 1
    val pixelCols = 5
    val pixelRows = 5
    val cellWidth = 1
    val cellHeight = 1
    createRasterSource(arr, tileCols, tileRows, pixelCols, pixelRows, cellWidth, cellHeight)
  }

  val testRasters = Map(
    "Raster1" -> createTile(
      Array(1, 0, 1, 0, 1,
            1, 0, 1, 0, 1,
            1, 0, 1, 0, 1,
            1, 0, 1, 0, 1,
            1, 0, 1, 0, 1)),
    "Raster2" -> createTile(
      Array(0, 0, 0, 0, 0,
            1, 1, 1, 1, 1,
            0, 0, 0, 0, 0,
            1, 1, 1, 1, 1,
            0, 0, 0, 0, 0)),
    "Raster3" -> createTile(
      Array(0, 0, 0, 0, 0,
            0, 1, 1, 1, 0,
            0, 1, 0, 1, 0,
            0, 1, 1, 1, 0,
            0, 0, 0, 0, 0)),
    "Raster4" -> createTile(
      Array(1, 2, 3, 4, 5,
            1, 2, 3, 4, 5,
            1, 2, 3, 4, 5,
            1, 2, 3, 4, 5,
            1, 2, 3, 4, 5)),
    "Raster5" -> createTile(
      Array(1, 1, 1, 1, 1,
            2, 2, 2, 2, 2,
            3, 3, 3, 3, 3,
            4, 4, 4, 4, 4,
            5, 5, 5, 5, 5))
  )

  val logic = new MockModelingServiceLogic(testRasters)

  val extent = Extent(0, 0, 5, 5)
  val rasterExtent = RasterExtent(extent, 1, 1, 5, 5)

  test("Empty GeoJson should return 0 polygons") {
    assert(logic.getPolygons("").size == 0)
  }

  test("Invalid GeoJson should return 0 polygons") {
    assert(logic.getPolygons("foo bar").size == 0)
  }

  test("Empty FeatureCollection should return 0 polygons") {
    assert(logic.getPolygons(s"""{"type":"FeatureCollection","features":[]}""").size == 0)
  }

  test("Raster should remain unmodified with a weight of 1") {
    val layers = List("Raster3")
    val weights = List(1)
    val result = logic.weightedOverlay(layers, weights, rasterExtent)
    assert(tilesAreEqual(result.get,
      createTile(
        Array(0, 0, 0, 0, 0,
              0, 1, 1, 1, 0,
              0, 1, 0, 1, 0,
              0, 1, 1, 1, 0,
              0, 0, 0, 0, 0))), "\n" + result.get.asciiDraw)
  }

  test("Weighted overlay should multiply and add raster values together") {
    val layers = List("Raster1", "Raster2")
    val weights = List(1, 2)
    val result = logic.weightedOverlay(layers, weights, rasterExtent)
    // Should be Raster1 * 1 + Raster2 * 2.
    assert(tilesAreEqual(result.get,
      createTile(
        Array(1, 0, 1, 0, 1,
              3, 2, 3, 2, 3,
              1, 0, 1, 0, 1,
              3, 2, 3, 2, 3,
              1, 0, 1, 0, 1))), "\n" + result.get.asciiDraw)
  }

  test("Breaks should be non-zero with at least 1 weighted overlay") {
    val layers = List("Raster1", "Raster2")
    val weights = List(1, 0)
    val numBreaks = 10
    val model = logic.weightedOverlay(layers, weights, rasterExtent)
    val result = logic.getBreaks(model, numBreaks)
    result match {
      case Complete(breaks, _) =>
        assert(breaks.min == 0)
        // The max break is highest raster value multiplied by its weight (1 x 1).
        assert(breaks.max == 1)
        // The only values used in our test rasters are 0 and 1.
        assert(breaks.length == 2)
      case Error(message, trace) =>
        fail(message)
    }
  }

  test("Single polygon mask") {
    val rs = logic.createRasterSource("Raster3", rasterExtent)
    val poly: Polygon = Rectangle()
      .withWidth(3)
      .withHeight(3)
      .withLowerLeftAt(1, 1)
      .build
    val polyMask = poly :: Nil
    val result = logic.getMaskedModel(rs, polyMask, None)
    assert(tilesAreEqual(result.get,
      createTile(
        Array(n, n, n, n, n,
              n, 1, 1, 1, n,
              n, 1, 0, 1, n,
              n, 1, 1, 1, n,
              n, n, n, n, n))), "\n" + result.get.asciiDraw)
  }

  test("Multi polygon mask") {
    val rs = logic.createRasterSource("Raster1", rasterExtent)
    val poly1: Polygon = Rectangle()
      .withWidth(3)
      .withHeight(3)
      .withLowerLeftAt(0, 0)
      .build
    val poly2: Polygon = Rectangle()
      .withWidth(3)
      .withHeight(3)
      .withLowerLeftAt(2, 2)
      .build
    val polyMask = poly1 :: poly2 :: Nil
    val result = logic.getMaskedModel(rs, polyMask, None)
    assert(tilesAreEqual(result.get,
      createTile(
        Array(n, n, 1, 0, 1,
              n, n, 1, 0, 1,
              1, 0, 1, 0, 1,
              1, 0, 1, n, n,
              1, 0, 1, n, n))), "\n" + result.get.asciiDraw)
  }

  test("Layer mask") {
    // Mask by layer "Raster2" with values "1".
    val maskRs = logic.createRasterSource("Raster1", rasterExtent)
      .localMap { z => if (z == 1) 1 else NODATA }

    val rs = logic.createRasterSource("Raster2", rasterExtent)
    val layerMask = maskRs :: Nil
    val result = logic.getMaskedModel(rs, None, layerMask)
    assert(tilesAreEqual(result.get,
      createTile(
        Array(0, n, 0, n, 0,
              1, n, 1, n, 1,
              0, n, 0, n, 0,
              1, n, 1, n, 1,
              0, n, 0, n, 0))), "\n" + result.get.asciiDraw)
  }

  test("Layer mask from string") {
    val rs = logic.createRasterSource("Raster2", rasterExtent)
    val layerMask = Map("Raster1" -> Array(1))
    val result = logic.getMaskedModel(rs, None, Some(layerMask), rasterExtent)
    assert(tilesAreEqual(result.get,
      createTile(
        Array(0, n, 0, n, 0,
              1, n, 1, n, 1,
              0, n, 0, n, 0,
              1, n, 1, n, 1,
              0, n, 0, n, 0))), "\n" + result.get.asciiDraw)
  }

  test("Combine polygon and layer mask") {
    val maskRs = logic.createRasterSource("Raster1", rasterExtent)
      .localMap { z => if (z == 1) 1 else NODATA }
    val layerMask = maskRs :: Nil

    val poly: Polygon = Rectangle()
      .withWidth(3)
      .withHeight(3)
      .withLowerLeftAt(2, 2)
      .build
    val polyMask = poly :: Nil

    val rs = logic.createRasterSource("Raster2", rasterExtent)
    val result = logic.getMaskedModel(rs, polyMask, layerMask)
    assert(tilesAreEqual(result.get,
      createTile(
        Array(n, n, 0, n, 0,
              n, n, 1, n, 1,
              n, n, 0, n, 0,
              n, n, n, n, n,
              n, n, n, n, n))), "\n" + result.get.asciiDraw)
  }

  test("Layer mask with multiple values") {
    val rs = logic.createRasterSource("Raster4", rasterExtent)
    // Should mask by (cells marked "1" in Raster3
    //                 AND cells marked "2" OR "4" in Raster5)
    val layerMask = Map("Raster3" -> Array(1),
                        "Raster5" -> Array(2, 4))
    val result = logic.getMaskedModel(rs, None, Some(layerMask), rasterExtent)
    assert(tilesAreEqual(result.get,
      createTile(
        Array(n, n, n, n, n,
              n, 2, 3, 4, n,
              n, n, n, n, n,
              n, 2, 3, 4, n,
              n, n, n, n, n))), "\n" + result.get.asciiDraw)
  }
}

