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
  "ch.megard" %% "akka-http-cors" % "0.1.10",
  // Begin Rollbar
  "com.storecove" %% "rollbar-scala" % "1.0",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
  "org.json4s" %% "json4s-jackson" % "3.2.11",
  "org.slf4j" % "slf4j-api" % "1.7.12"
  // End Rollbar
)

Revolver.settings
