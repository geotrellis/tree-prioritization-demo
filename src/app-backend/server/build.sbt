name := "otm-modeling-server"

libraryDependencies ++= Seq(
  "org.locationtech.geotrellis" %% "geotrellis-spark"      % Version.geotrellis,
  "org.locationtech.geotrellis" %% "geotrellis-s3"         % Version.geotrellis,
  "com.typesafe.akka" %% "akka-actor"           % Version.akkaActor,
  "com.typesafe.akka" %% "akka-http-core"       % Version.akkaHttp,
  "com.typesafe.akka" %% "akka-http"            % Version.akkaHttp,
  "com.typesafe.akka" %% "akka-http-spray-json" % Version.akkaHttp,
  "org.apache.spark" %% "spark-core"    % Version.spark,
  "org.apache.hadoop" % "hadoop-client" % Version.hadoop,
  "org.scalatest"    %% "scalatest"     % Version.scalaTest % "test",
  "com.iheart"       %% "ficus"         % Version.ficus,
  "ch.megard" %% "akka-http-cors" % "0.1.10"
)

Revolver.settings
