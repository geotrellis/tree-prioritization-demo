import sbt._
import sbt.Keys._
import scala.util.Properties

// sbt-assembly
import sbtassembly.Plugin._
import AssemblyKeys._

object Version {
  def either(environmentVariable: String, default: String): String =
    Properties.envOrElse(environmentVariable, default)

  val modeling     = "1.5.1"

  val geotools     = "8.0-M4"
  val geotrellis   = "0.10.1"
  val scala        = "2.10.6"
  val scalatest    = "2.2.1"
  val spray        = "1.3.2"
  val sprayJson    = "1.2.6"
  lazy val hadoop  = either("SPARK_HADOOP_VERSION", "2.6.0")
  lazy val spark   = either("SPARK_VERSION", "1.5.2")
}

object OTMModelingBuild extends Build {
  val resolutionRepos = Seq(
    "Local Maven Repository"  at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    "Typesafe Repo"           at "http://repo.typesafe.com/typesafe/releases/",
    "spray repo"              at "http://repo.spray.io/",
    "Geotools" at "http://download.osgeo.org/webdav/geotools/",
    "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases"
  )

  // Default settings
  override lazy val settings =
    super.settings ++
  Seq(
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " },
    version := Version.modeling,
    scalaVersion := Version.scala,
    organization := "org.opentreemap.modeling",

    // disable annoying warnings about 2.10.x
    conflictWarning in ThisBuild := ConflictWarning.disable,
    scalacOptions ++=
      Seq("-deprecation",
        "-unchecked",
        "-Yinline-warnings",
        "-language:implicitConversions",
        "-language:reflectiveCalls",
        "-language:higherKinds",
        "-language:postfixOps",
        "-language:existentials",
        "-feature"),

    publishMavenStyle := true,

    publishArtifact in Test := false,

    pomIncludeRepository := { _ => false },
    licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))
  )

  val defaultAssemblySettings =
    assemblySettings ++
  Seq(
    test in assembly := {},
    mergeStrategy in assembly <<= (mergeStrategy in assembly) {
      (old) => {
        case "reference.conf" => MergeStrategy.concat
        case "application.conf" => MergeStrategy.concat
        case "META-INF/MANIFEST.MF" => MergeStrategy.discard
        case "META-INF\\MANIFEST.MF" => MergeStrategy.discard
        case _ => MergeStrategy.first
      }
    },
    resolvers ++= resolutionRepos
  )

  lazy val root: Project =
    Project("otm-modeling", file(".")).aggregate(tile)

  lazy val rootSettings =
    Seq(
      organization := "org.opentreemap.modeling",

      scalaVersion := Version.scala,

      fork := true,
      // raise memory limits here if necessary
      javaOptions += "-Xmx2G",
      javaOptions += "-Djava.library.path=/usr/local/lib",

      libraryDependencies ++= Seq(
        "com.azavea.geotrellis" %% "geotrellis-spark" % Version.geotrellis,
        "com.azavea.geotrellis" %% "geotrellis-s3" % Version.geotrellis,
        "io.spray" %% "spray-routing" % Version.spray,
        "io.spray" %% "spray-json" % Version.sprayJson,
        "io.spray" %% "spray-can" % Version.spray,
        "org.apache.spark" %% "spark-core" % Version.spark % "provided",
        "org.apache.hadoop" % "hadoop-client" % Version.hadoop % "provided",
        // Begin Rollbar
        "com.storecove" %% "rollbar-scala" % "1.0",
        "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
        "org.json4s" %% "json4s-jackson" % "3.2.11",
        "org.slf4j" % "slf4j-api" % "1.7.12"
        // End Rollbar
      ),

      unmanagedResourceDirectories in Compile <+= baseDirectory / "data"
    ) ++ defaultAssemblySettings


  lazy val tile = Project("tile",  file("tile"))
    .settings(tileSettings:_*)

  lazy val tileSettings =
    Seq(
      name := "otm-modeling",
      jarName in assembly := s"otm-modeling-${Version.modeling}.jar",
      mainClass := Some("org.opentreemap.modeling.Main")
    ) ++ rootSettings

}
