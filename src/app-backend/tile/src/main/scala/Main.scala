package org.opentreemap.modeling

import akka.io.IO
import akka.actor.Props
import com.typesafe.config.ConfigFactory
import spray.can.Http

object Main {
  def main(args: Array[String]) {
    // create and start our service actor
    implicit val actorSystem: akka.actor.ActorSystem = akka.actor.ActorSystem("GeoTrellis", ConfigFactory.load())

    val service = actorSystem.actorOf(Props(classOf[TileServiceActor]), "opentreemap-modeling-tile-service")

    // start a new HTTP server with the service actor as the handler
    IO(Http) ! Http.Bind(service, TileServiceConfig.host, port = TileServiceConfig.port)
  }
}
