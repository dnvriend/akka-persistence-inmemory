import bintray.Plugin._

seq(bintraySettings:_*)

organization := "com.github.dnvriend"

name := "akka-persistence-inmemory"

version := "1.0.0"

scalaVersion := "2.11.1"

crossScalaVersions := Seq("2.10.4", "2.11.2")

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

libraryDependencies ++= {
    val akkaVersion = "2.3.6"
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

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("akka", "elasticsearch", "lucene", "cluster", "index", "indexing")

bintray.Keys.packageAttributes in bintray.Keys.bintray ~=
  ((_: bintray.AttrMap) ++ Map("website_url" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-inmemory.git")), "github_repo" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-inmemory.git")), "issue_tracker_url" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-inmemory.git/issues/"))))