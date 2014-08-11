package org.opentreemap.modeling.test

import org.scalatest._
import geotrellis.engine._
import geotrellis.testkit._
import geotrellis.raster._

abstract class UnitSpec extends FunSuite with TileBuilders {
  /** Return true if tiles match. */
  def tilesAreEqual(actual: Tile, expected: Tile): Boolean = {
    if (expected.cols != actual.cols)
      return false
    if (expected.rows != actual.rows)
      return false

    for (row <- 0 until expected.rows) {
      for (col <- 0 until expected.cols) {
        if (expected.get(col, row) != actual.get(col, row))
          return false
      }
    }

    return true
  }
}

