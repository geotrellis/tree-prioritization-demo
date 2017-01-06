package org.opentreemap.modeling

import com.storecove.rollbar.RollbarNotifierFactory
import org.slf4j.MDC

import scala.collection.JavaConversions._
import scala.collection.mutable

trait Rollbar {

  def postInfoToRollbar(message:String): Unit = {
    postToRollbar("INFO", message, None)
  }

  def postExceptionToRollbar(ex:Throwable): Unit = {
    postToRollbar("ERROR", ex.getMessage, Some(ex))
  }

  private def postToRollbar(level:String, message:String, ex:Option[Throwable]): Unit = {
    val accessToken = TileServiceConfig.rollbarAccessToken
    val environment = TileServiceConfig.otmStackType
    val notifier = RollbarNotifierFactory.getNotifier(accessToken, environment)
    notifier.notify(level, message, ex, getMDC)
  }

  private def getMDC = {
    val mdc = MDC.getCopyOfContextMap
    if (mdc == null) {
      mutable.Map.empty[String, String]
    } else {
      mapAsScalaMap(mdc)
    }
  }
}
