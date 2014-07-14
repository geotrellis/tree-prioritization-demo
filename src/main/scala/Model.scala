package org.opentreemap.modeling

import geotrellis.Raster
import geotrellis.RasterExtent
import geotrellis.TypeByte
import geotrellis.isData
import geotrellis.NODATA
import geotrellis.source.SeqSource
import geotrellis.source.ValueSource
import geotrellis.source.RasterSource
import geotrellis.raster.op.zonal.summary.FullTileIntersection
import geotrellis.raster.op.zonal.summary.PartialTileIntersection
import geotrellis.feature.Polygon
import geotrellis.feature.Geometry
import geotrellis.feature.rasterize.Callback

import geotrellis.feature.rasterize.Rasterizer

import com.vividsolutions.jts.{ geom => jts }

case class LayerSummary(name: String, score: Double)
case class SummaryResult(layerSummaries: List[LayerSummary], score: Double)

case class LayerRatio(sum: Int, count: Int) {
  def value = sum / count.toDouble
  def combine(other: LayerRatio) = {
    LayerRatio(sum + other.sum, count + other.count)
  }
}

object LayerRatio {
  private def rasterIter(r: Raster): Iterable[Int] =
    for {col <- 0 until r.cols
         row <- 0 until r.rows} yield r.get(col, row)

  def rasterResult(r: Raster): LayerRatio = {
    val sum = rasterIter(r) filter { _ != NODATA } reduce { _ + _ }
    LayerRatio(sum, r.length)
  }
}

object Model {
  def weightedOverlay(layers: Iterable[String],
                      weights: Iterable[Int],
                      rasterExtent: RasterExtent): RasterSource =
    layers
      .zip(weights)
      .map { case (layer, weight) =>
        RasterSource(layer, rasterExtent)
          .convert(TypeByte)
          .localMultiply(weight)
       }
      .localAdd

  def summary(layers: Iterable[String],
              weights: Iterable[Int],
              polygon: jts.Geometry): ValueSource[SummaryResult] = {
    val layerRatios: SeqSource[LayerSummary] =
      layers
        .zip(weights)
        .map { case (layer, weight) =>
          //val tileCache = Main.getCachedRatios(layer)
          RasterSource(layer)
            //.mapIntersecting(Polygon(polygon,0), tileCache) {
            .mapIntersecting(Polygon(polygon, 0)) { tileIntersection =>
              tileIntersection match {
                case FullTileIntersection(tile) =>
                  LayerRatio.rasterResult(tile)
                case PartialTileIntersection(tile, intersections) =>
                  var sum: Int = 0
                  var total: Int = 0
                  val f = new Callback[Geometry, Any] {
                    def apply(col: Int, row: Int, g: Geometry[Any]) {
                      total += 1
                      val z = tile.get(col, row)
                      if (isData(z)) { sum += z }
                    }
                  }
                  intersections.foreach { g =>
                    Rasterizer.foreachCellByFeature(g, tile.rasterExtent)(f)
                  }
                  LayerRatio(sum, total)
              }
            }
            .foldLeft(LayerRatio(0, 0)) { (l1, l2) =>
              l1.combine(l2)
            }
            .map { ratio =>
              LayerSummary(layer, ratio.value * weight)
            }
        }

    layerRatios
      .foldLeft(SummaryResult(List[LayerSummary](), 0.0)) { (result, layerSummary) =>
        SummaryResult(
          (layerSummary :: result.layerSummaries),
          result.score + layerSummary.score
        )
      }
  }
}

