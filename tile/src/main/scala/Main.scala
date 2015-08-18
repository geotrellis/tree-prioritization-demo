package org.opentreemap.modeling.tile

import geotrellis.engine._

import akka.io.IO
import akka.actor.Props
import spray.can.Http

object Main {
  def main(args: Array[String]) {
    implicit val system = GeoTrellis.engine.system

    // create and start our the service actor
    val service =
      system.actorOf(Props(classOf[TileServiceActor]), "opentreemap-modeling-tile-service")

    // start a new HTTP server with the service actor as the handler
    IO(Http) ! Http.Bind(service, TileServiceConfig.host, port = TileServiceConfig.port)
  }
}
