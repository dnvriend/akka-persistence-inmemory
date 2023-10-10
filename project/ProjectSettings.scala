import sbt._
import sbt.Keys._
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform

object ProjectSettings extends AutoPlugin {
  final val PekkoV = "1.0.1"
  final val scala212V = "2.12.18"
  final val scala213V = "2.13.11"
  final val scalaV = scala213V
  final val ScalazV = "7.3.7"
  final val ScalaTestV = "3.2.15"
  final val ScalaXmlV = "2.1.0"
  final val LogbackV = "1.4.7"
  final val version = "1.0.1"

  override def requires = plugins.JvmPlugin && SbtScalariform
  override def trigger = allRequirements

  override def projectSettings = Seq(
    name := "pekko-persistence-inmemory",
    organization := "io.github.alstanchev",
    organizationName := "Aleksandar Stanchev",
    description := "A plugin for storing events in an event journal pekko-persistence-inmemory",
    startYear := Some(2023),

    scalaVersion := scalaV,
    crossScalaVersions := Seq(scala212V, scala213V),
    crossVersion := CrossVersion.binary,

    licenses := Seq(("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))),

  ) ++ compilerSettings ++ scalariFormSettings ++ resolverSettings ++ librarySettings ++ testSettings

  lazy val librarySettings = Seq(
    libraryDependencies += "org.apache.pekko" %% "pekko-actor" % PekkoV,
    libraryDependencies += "org.apache.pekko" %% "pekko-persistence" % PekkoV,
    libraryDependencies += "org.apache.pekko" %% "pekko-persistence-query" % PekkoV,
    libraryDependencies += "org.apache.pekko" %% "pekko-stream" % PekkoV,
    libraryDependencies += "org.scalaz" %% "scalaz-core" % ScalazV,
    libraryDependencies += "ch.qos.logback" % "logback-classic" % LogbackV % Test,
    libraryDependencies += "org.apache.pekko" %% "pekko-slf4j" % PekkoV % Test,
    libraryDependencies += "org.apache.pekko" %% "pekko-persistence-tck" % PekkoV % Test,
    libraryDependencies += "org.apache.pekko" %% "pekko-stream-testkit" % PekkoV % Test,
    libraryDependencies += "org.apache.pekko" %% "pekko-testkit" % PekkoV % Test,
    libraryDependencies += "org.scalatest" %% "scalatest" % ScalaTestV % Test,
    libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % ScalaXmlV % Test,

  )

  lazy val testSettings = Seq(
    Test / fork := true,
    Test / logBuffered := false,
    Test / parallelExecution := false,
    // show full stack traces and test case durations
    Test / testOptions += Tests.Argument("-oDF"),
  )

  lazy val scalariFormSettings = Seq(
    SbtScalariform.autoImport.scalariformPreferences := {
      SbtScalariform.autoImport.scalariformPreferences.value
        .setPreference(AlignSingleLineCaseStatements, true)
        .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
        .setPreference(DoubleIndentConstructorArguments, true)
        .setPreference(DanglingCloseParenthesis, Preserve)
    }
  )

  lazy val resolverSettings = Seq(
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    resolvers += "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases",
    resolvers += "Apache OSS Releases" at "https://repository.apache.org/content/repositories/releases/",
    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += Resolver.jcenterRepo,
  )

  lazy val compilerSettings = Seq(
    scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) => Seq(
        "-encoding",
        "UTF-8",
        "-deprecation",
        "-feature",
        "-unchecked",
        "-Xlog-reflective-calls",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-Ypartial-unification", // This option is specific to 2.12
        "-target:jvm-1.8",
        "-Ydelambdafy:method"
      )
      case Some((2, 13)) => Seq(
        "-encoding",
        "UTF-8",
        "-deprecation",
        "-feature",
        "-unchecked",
        "-Xlog-reflective-calls",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-target:jvm-1.8",
        "-Ydelambdafy:method"
      )
      case Some((3, _)) => Seq(
        "-encoding",
        "UTF-8",
        "-feature",
        "-unchecked",
        "-Xlog-reflective-calls"
        // Other Scala 3-specific options
      )
      case _ => Seq()
    })
  )
}
