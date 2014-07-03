package org.opentreemap.modeling

import akka.actor.Actor
import spray.routing.HttpService

class ModelingServiceActor() extends Actor with ModelingService {
  def actorRefFactory = context
  def receive = runRoute(serviceRoute)
}

trait ModelingService extends HttpService {
  implicit def executionContext = actorRefFactory.dispatcher

  val serviceRoute = {
    get {
      complete {
        "OpenTreeMap Modeling and Prioritization"
      }
    }
  }
}
