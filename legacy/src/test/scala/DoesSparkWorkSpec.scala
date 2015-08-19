package org.opentreemap.modeling.test

import geotrellis.spark._
import geotrellis.raster._
import geotrellis.raster.histogram._

import geotrellis.vector._

import scala.collection.mutable
import org.scalatest._
import spire.syntax.cfor._

class DoesSparkWorkSpec extends FunSpec with Matchers with OnlyIfCanRunSpark {
  ifCanRunSpark {
    describe("Spark setup") {
      it("can create a RasterRDD") {
        val dataPath = TestFiles.dataPath
        val p = s"$dataPath/NLCD_DE-clipped.tif"
        val minMax = TestFiles.delawareNLCD.minMax
      }
    }
  }
}
