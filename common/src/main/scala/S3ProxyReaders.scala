package org.opentreemap.modeling

import com.amazonaws.Protocol
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.s3.{S3ClientOptions, AmazonS3Client => AWSAmazonS3Client}
import geotrellis.spark.io.AttributeStore
import geotrellis.spark.io.s3._
import org.apache.spark.SparkContext


class S3ProxyLayerReader(attributeStore: AttributeStore)(implicit sc: SparkContext)
  extends S3LayerReader(attributeStore) {
  override def rddReader =
    new S3RDDReader {
      def getS3Client = () => S3ProxyClient()
    }
}

class S3ProxyValueReader(attributeStore: AttributeStore)
  extends S3ValueReader(attributeStore) {
  override val s3Client = S3ProxyClient()
}

object S3ProxyValueReader {
  def apply(bucket: String, prefix: String): S3ValueReader =
    new S3ProxyValueReader(new S3AttributeStore(bucket, prefix))
}

object S3ProxyClient {
  def apply() = {
    val config = S3Client.defaultConfiguration
    config.setProtocol(Protocol.HTTP)
    val client = new AWSAmazonS3Client(new DefaultAWSCredentialsProviderChain(), config)
    // Generate "path-style" URLs (s3.amazonaws.com/bucket/object)
    // instead of "virtual-hosted-style" URLs (bucket.s3.amazonaws.com/object)
    // because that's what our cacheing proxy is expecting
    client.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true))
    // TODO: get endpoint from environment variable and communicate via spark broadcast
    // (so as to avoid Spark "object not serializable" runtime exception).
    client.setEndpoint("http://s3-proxy")
    new AmazonS3Client(client)
  }
}
