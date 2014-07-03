package org.opentreemap.modeling

import geotrellis.process._
import geotrellis._
import geotrellis.source._
import geotrellis.raster._
import geotrellis.raster.op._
import geotrellis.feature._

import akka.actor.{ ActorRef, Props, Actor, ActorSystem }
import akka.routing.FromConfig

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http

object Main {

  def main(args: Array[String]): Unit = {

    implicit val system = server.system

    // create and start our the service actor
    val service = 
      system.actorOf(Props(classOf[ModelingServiceActor]), "opentreemap-modeling-service")

    // start a new HTTP server with the service actor as the handler
    IO(Http) ! Http.Bind(service, ServiceConfig.host, port = ServiceConfig.port)
  }
}
