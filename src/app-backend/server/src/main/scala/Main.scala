package org.opentreemap.modeling


import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import geotrellis.spark.io.file._
import geotrellis.spark.io.s3._

import scala.concurrent.ExecutionContext.Implicits.global

object AkkaSystem {
  implicit val system = ActorSystem("otm-modeling-server")
  implicit val materializer = ActorMaterializer()

  trait LoggerExecutor {
    protected implicit val log = Logging(system, "app")
  }
}

object Main extends Router with ReaderSet {
  import AkkaSystem._
  import TileServiceConfig._

  lazy val (attributeStore, tileReader, collectionReader) =
    if(isS3Catalog) {
      val as = S3AttributeStore(S3CatalogPath._1, S3CatalogPath._2)
      val vr = new S3ValueReader(as)
      val cr = S3CollectionLayerReader(as)

      (as, vr, cr)
    } else {
      val as = FileAttributeStore(catalogPath)
      val vr = FileValueReader(as)
      val cr = FileCollectionLayerReader(as)

      (as, vr, cr)
    }

  def main(args: Array[String]): Unit = {
    Http().bindAndHandle(routes, configHost, configPort)
  }
}
