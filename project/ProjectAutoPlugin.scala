import sbt._
import sbt.Keys._
import de.heikoseeberger.sbtheader._
import de.heikoseeberger.sbtheader.HeaderKey._
import de.heikoseeberger.sbtheader.license.Apache2_0
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

import scalariform.formatter.preferences.FormattingPreferences

// A plugin extends the build definition, most commonly by adding new settings. The new settings could be new tasks.
// Plugins usually provide settings that get added to a project to enable the plugin’s functionality.
//
// A plugin can declare that its settings be automatically added to the build definition, 
// in which case you don’t have to do anything to add them.
//
// If you’re using an auto plugin that requires explicit enablement,
// then you have to add the following to your build.sbt: enablePlugin(FooPlugin, BarPlugin)
// The 'enablePlugins' method allows projects to explicitly define the auto plugins they wish to consume.
// Projects can also exclude plugins using the 'disablePlugins' method
//
// Auto plugins should document whether they need to be explicitly enabled
//
// If you’re curious which auto plugins are enabled for a given project, just run the 'plugins' command on the sbt console.
//
// With auto plugins, all settings are provided by the plugin directly via the 'projectSettings' method.
//
// If the plugin needs to append settings at the build-level (that is, in ThisBuild) there’s a 'buildSettings' method.
//
// The 'globalSettings' is appended once to the global settings (in Global). 
// These allow a plugin to automatically provide new functionality or new defaults.
//
// ====================
// Plugin Dependencies:
// ====================
// The 'requires' method returns a value of type Plugins, which is a DSL 
// for constructing the dependency list. The requires method typically contains 
// one of the following values:
// empty (No plugins, this is the default)
// other auto plugins
// && operator (for defining multiple dependencies)
//
// For example, we might want to create a triggered plugin that can append commands 
// automatically to the build. To do this, set the requires method to return empty 
// (this is the default), and override the trigger method with allRequirements.
// Projects can also exclude plugins using the disablePlugins method
//
object ProjectAutoPlugin extends AutoPlugin {
  final val AkkaVersion = "2.5.1"
  final val ScalazVersion = "7.2.12"
  final val ScalaTestVersion = "3.0.3"
  final val LogbackVersion = "1.2.3"

  final val formattingPreferences: FormattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
      .setPreference(DoubleIndentClassDeclaration, true)
  }

  override val requires = com.typesafe.sbt.SbtScalariform

  override val trigger: PluginTrigger = allRequirements

  object autoImport {
  }

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] = SbtScalariform.scalariformSettings ++ Seq(
    name := "akka-persistence-inmemory",
    organization := "com.github.dnvriend",
    organizationName := "Dennis Vriend",
    description := "A plugin for storing events in an event journal akka-persistence-inmemory",
    startYear := Some(2014),

    licenses += ("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php")),

    scalaVersion := "2.12.2",
    crossScalaVersions := Seq("2.11.11", "2.12.2"),
    crossVersion := CrossVersion.binary,

    fork in Test := true,

    logBuffered in Test := false,

    parallelExecution in Test := false,

    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlog-reflective-calls",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-target:jvm-1.8"
    ),

    scalacOptions += "-Ypartial-unification",
    scalacOptions += "-Ydelambdafy:method",

      // show full stack traces and test case durations
    testOptions in Test += Tests.Argument("-oDF"),

    headers := headers.value ++ Map(
      "scala" -> Apache2_0("2017", "Dennis Vriend"),
      "conf" -> Apache2_0("2017", "Dennis Vriend", "#")
    ),

    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += Resolver.jcenterRepo,

    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test := formattingPreferences,

   libraryDependencies += "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
   libraryDependencies += "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
   libraryDependencies += "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
   libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
   libraryDependencies += "org.scalaz" %% "scalaz-core" % ScalazVersion,
   libraryDependencies += "ch.qos.logback" % "logback-classic" % LogbackVersion % Test,
   libraryDependencies +="com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
   libraryDependencies +="com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
   libraryDependencies +="com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
   libraryDependencies +="com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
   libraryDependencies +="org.scalatest" %% "scalatest" % ScalaTestVersion % Test
   )
}