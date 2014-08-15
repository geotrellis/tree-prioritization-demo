import AssemblyKeys._

name := "opentreemap-modeling"

scalaVersion := "2.10.0"

resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    "spray" at "http://repo.spray.io/",
    "Geotools" at "http://download.osgeo.org/webdav/geotools/"
)

libraryDependencies ++= Seq(
  "com.azavea.geotrellis" %% "geotrellis-services" % "0.10.0-SNAPSHOT",
  "com.azavea.geotrellis" %% "geotrellis-testkit" % "0.10.0-SNAPSHOT" % "test",
  "io.spray" % "spray-routing" % "1.2.1",
  "io.spray" %% "spray-json" % "1.2.6",
  "io.spray" % "spray-can" % "1.2.1",
  "org.scalatest" % "scalatest_2.10" % "2.2.1" % "test",
  "org.geotools" % "gt-main" % "8.0-M4",
  "org.geotools" % "gt-coveragetools" % "8.0-M4"
)

seq(Revolver.settings: _*)

assemblySettings

mergeStrategy in assembly <<= (mergeStrategy in assembly) {
  (old) => {
    case "reference.conf" => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case "META-INF\\MANIFEST.MF" => MergeStrategy.discard
    case _ => MergeStrategy.first
  }
}

unmanagedResourceDirectories in Compile <+= baseDirectory { _/"data"}
