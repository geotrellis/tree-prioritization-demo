package org.opentreemap.modeling.test

import org.opentreemap.modeling._
import org.opentreemap.modeling.ModelingTypes._

import org.scalatest._
import geotrellis.engine._
import geotrellis.engine.op.local._
import geotrellis.vector._
import geotrellis.raster._

import geotrellis.testkit.vector._

class HistogramLogicSpec extends FunSuite {

  // TODO: Rewrite tests to use RasterRDDs

  /**************************************************

  test("Histogram") {
    val polyMask = Nil
    val rs = logic.createRasterSource("Raster5", rasterExtent)
    val summary =  logic.histogram(rs, polyMask)
    summary.run match {
      case Complete(result, h) =>
        assert(result.getTotalCount == 25)
        assert(result.getMinValue == 1)
        assert(result.getMaxValue == 5)
        assert(result.getItemCount(1) == 5)
        assert(result.getItemCount(2) == 5)
        assert(result.getItemCount(3) == 5)
        assert(result.getItemCount(4) == 5)
        assert(result.getItemCount(5) == 5)
      case Error(message, trace) =>
        fail(message)
    }
  }

  test("Histogram with polygon mask") {
    val poly: Polygon = Rectangle()
      .withWidth(3)
      .withHeight(3)
      .withLowerLeftAt(0, 0)
      .build
    val polyMask = poly :: Nil

    val rs = logic.createRasterSource("Raster5", rasterExtent)
    val summary =  logic.histogram(rs, polyMask)
    summary.run match {
      case Complete(result, h) =>
        assert(result.getTotalCount == 9)
        assert(result.getMinValue == 3)
        assert(result.getMaxValue == 5)
        assert(result.getItemCount(1) == 0)
        assert(result.getItemCount(2) == 0)
        assert(result.getItemCount(3) == 3)
        assert(result.getItemCount(4) == 3)
        assert(result.getItemCount(5) == 3)
      case Error(message, trace) =>
        fail(message)
    }
  }

  ****************************************/
}
