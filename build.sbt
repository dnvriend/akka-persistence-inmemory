organization := "com.github.dnvriend"

name := "akka-persistence-inmemory"

version := "1.1.0-M3"

scalaVersion := "2.11.6"

libraryDependencies ++= {
    val akkaVersion = "2.4-M3"
    Seq(
    "com.typesafe.akka"   %% "akka-actor"                           % akkaVersion,
    "com.typesafe.akka"   %% "akka-slf4j"                           % akkaVersion,
    "com.typesafe.akka"   %% "akka-persistence"                     % akkaVersion,
    "com.typesafe.akka"   %% "akka-testkit"                         % akkaVersion     % "test",
    "com.typesafe.akka"   %% "akka-persistence-tck"                 % akkaVersion     % "test",
    "org.scalatest"       %% "scalatest"                            % "2.1.4"         % "test"
  )
}

autoCompilerPlugins := true

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

publishMavenStyle := true

licenses += ("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php"))

parallelExecution in Test := false