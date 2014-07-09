package org.opentreemap.modeling

import org.geotools.referencing.CRS

object Projections {
  val WebMercator = CRS.decode("EPSG:3857")
  val LatLong = CRS.decode("EPSG:4326")
}

