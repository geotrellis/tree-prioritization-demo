package org.opentreemap.modeling

import geotrellis.spark._
import geotrellis.spark.io._

trait ReaderSet {
  val tileReader: ValueReader[LayerId]
  val collectionReader: CollectionLayerReader[LayerId]
  val attributeStore: AttributeStore
}
