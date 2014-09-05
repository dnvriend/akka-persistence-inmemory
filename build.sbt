import SonatypeKeys._

organization := "com.github.dnvriend"

name := "akka-persistence-inmemory"

version := "0.0.1"

scalaVersion := "2.11.1"

crossScalaVersions := Seq("2.10.4", "2.11.2")

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

profileName := "com.github.dnvriend"

libraryDependencies ++= {
    val akkaVersion = "2.3.6"
    Seq(
    "com.typesafe.akka"   %% "akka-actor"                    % akkaVersion,
    "com.typesafe.akka"   %% "akka-slf4j"                    % akkaVersion,
    "com.typesafe.akka"   %% "akka-persistence-experimental" % akkaVersion,
    "ch.qos.logback"       %  "logback-classic"              % "1.1.2",
    "org.slf4j"            % "slf4j-nop"                     % "1.6.4",
    "com.typesafe.akka"   %% "akka-testkit"                  % akkaVersion     % "test",
    "org.scalatest"       %% "scalatest"                     % "2.1.4"         % "test",
    "com.github.krasserm" %% "akka-persistence-testkit"      % "0.3.4"         % "test"
  )
}

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")

// publish settings

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/dnvriend/akka-persistence-inmemory</url>
  <licenses>
    <license>
      <name>BSD-style</name>
      <url>http://www.opensource.org/licenses/bsd-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>https://github.com/dnvriend/akka-persistence-inmemory</url>
    <connection>scm:git:git@github.com:dnvriend/akka-persistence-inmemory.git</connection>
  </scm>
  <developers>
    <developer>
      <id>you</id>
      <name>Dennis Vriend</name>
      <url>https://github.com/dnvriend</url>
    </developer>
  </developers>
)

xerial.sbt.Sonatype.sonatypeSettings
