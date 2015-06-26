import sbt._
import sbt.Keys._
import scala.util.Properties

// sbt-assembly
import sbtassembly.Plugin._
import AssemblyKeys._

object Version {
  def either(environmentVariable: String, default: String): String =
    Properties.envOrElse(environmentVariable, default)

  val geotools     = "8.0-M4"
  val geotrellis   = "0.10.0-M1"
  val scala        = "2.10.5"
  val scalatest    = "2.2.1"
  val spray        = "1.3.2"
  val sprayJson    = "1.2.6"
  lazy val hadoop  = either("SPARK_HADOOP_VERSION", "2.6.0")
  lazy val spark   = either("SPARK_VERSION", "1.2.0")
}

object OTMModelingBuild extends Build {
  val resolutionRepos = Seq(
    "Local Maven Repository"  at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    Resolver.sonatypeRepo("snapshots"),
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
    version := "0.0.1",
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

  lazy val modeling: Project =
    Project("modeling", file("."))
      .settings(modelingSettings:_*)

  lazy val modelingSettings =
    Seq(
      organization := "org.opentreemap.modeling",
      name := "modeling",

      scalaVersion := Version.scala,

      fork := true,
      // raise memory limits here if necessary
      javaOptions += "-Xmx2G",
      javaOptions += "-Djava.library.path=/usr/local/lib",

      libraryDependencies ++= Seq(
        "com.azavea.geotrellis" %% "geotrellis-engine" % Version.geotrellis,
        "com.azavea.geotrellis" %% "geotrellis-services" % Version.geotrellis,
        "com.azavea.geotrellis" %% "geotrellis-spark" % Version.geotrellis,
        "com.azavea.geotrellis" %% "geotrellis-testkit" % Version.geotrellis % "test",
        "io.spray" %% "spray-routing" % Version.spray,
        "io.spray" %% "spray-json" % Version.sprayJson,
        "io.spray" %% "spray-can" % Version.spray,
        "org.scalatest" %% "scalatest" % Version.scalatest % "test",
        "org.apache.spark" %% "spark-core" % Version.spark,
        // TODO: SPARK CORE SHOULD BE "provided"
        /*

I hit this error

modeling > run
[info] Running org.opentreemap.modeling.Main
[error] Uncaught error from thread [GeoTrellis-akka.actor.default-dispatcher-3] shutting
 down JVM since 'akka.jvm-exit-on-fatal-error' is enabled for ActorSystem[GeoTrellis]
[error] java.lang.NoClassDefFoundError: org/apache/spark/Logging

Eugene explained

yes, spark and hadoop are marked as “provided”, which means they are not present in run context classPath.
This happens because spark programs are usually expected to be run by `spark-submit` command.
You can do three things: use spark-submit (pain), run your stuff from test, remove provided.
I don’t recommend removing provided, because that’s not how your stuff will be run in production and it will make you jump through some hoops managing spark context.
So you best best would be to run it from test context.
maybe have some like `object LocalServer extends App {…}`. And you can run it with `test:run`
I haven’t ever actually done that, but should work out.

To get things working I TEMPORARILY IGNORED HIM and removed "provided"

         */
        // "org.apache.spark" %% "spark-core" % Version.spark % "provided",
        "org.apache.hadoop" % "hadoop-client" % Version.hadoop % "provided"
      ),

      unmanagedResourceDirectories in Compile <+= baseDirectory / "data"
    ) ++
  defaultAssemblySettings
}
