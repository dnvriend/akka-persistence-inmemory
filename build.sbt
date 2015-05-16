organization := "com.github.dnvriend"

name := "akka-persistence-inmemory"

version := "1.0.1-SNAPSHOT"

scalaVersion := "2.11.6"

crossScalaVersions := Seq("2.10.5", "2.11.6")

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

libraryDependencies ++= {
    val akkaVersion = "2.3.11"
    Seq(
    "com.typesafe.akka"   %% "akka-actor"                    % akkaVersion,
    "com.typesafe.akka"   %% "akka-slf4j"                    % akkaVersion,
    "com.typesafe.akka"   %% "akka-persistence-experimental" % akkaVersion,
    "com.typesafe.akka"   %% "akka-testkit"                  % akkaVersion     % "test",
    "org.scalatest"       %% "scalatest"                     % "2.1.4"         % "test",
    "com.github.krasserm" %% "akka-persistence-testkit"      % "0.3.4"         % "test"
  )
}

autoCompilerPlugins := true

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

publishMavenStyle := true

licenses += ("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php"))

parallelExecution in Test := false