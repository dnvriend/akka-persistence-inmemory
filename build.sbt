organization := "com.github.dnvriend"

name := "akka-persistence-inmemory"

version := "1.1.3-RC3"

scalaVersion := "2.11.7"

libraryDependencies ++= {
    val akkaVersion = "2.4.0-RC3"
    Seq(
    "com.typesafe.akka"   %% "akka-actor"                           % akkaVersion,
    "com.typesafe.akka"   %% "akka-slf4j"                           % akkaVersion,
    "com.typesafe.akka"   %% "akka-persistence"                     % akkaVersion,
    "com.typesafe.akka"   %% "akka-persistence-query-experimental"  % akkaVersion,
    "com.typesafe.akka"   %% "akka-stream-testkit-experimental"     % "1.0"           % Test,
    "com.typesafe.akka"   %% "akka-testkit"                         % akkaVersion     % Test,
    "com.typesafe.akka"   %% "akka-persistence-tck"                 % akkaVersion     % Test,
    "org.scalatest"       %% "scalatest"                            % "2.2.4"         % Test
  )
}

autoCompilerPlugins := true

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

publishMavenStyle := true

licenses += ("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php"))

parallelExecution in Test := false

// enable scala code formatting //
import scalariform.formatter.preferences._

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(RewriteArrowSymbols, true)

// enable updating file headers //
import de.heikoseeberger.sbtheader.license.Apache2_0

headers := Map(
    "scala" -> Apache2_0("2015", "Dennis Vriend"),
    "conf" -> Apache2_0("2015", "Dennis Vriend", "#")
)

enablePlugins(AutomateHeaderPlugin)