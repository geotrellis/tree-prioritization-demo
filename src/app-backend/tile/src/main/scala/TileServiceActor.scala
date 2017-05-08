package org.opentreemap.modeling

import akka.actor.Actor

class TileServiceActor extends Actor with TileService {
  override def actorRefFactory = context
  override def receive = runRoute(serviceRoute)
}
