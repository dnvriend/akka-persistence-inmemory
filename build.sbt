/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

name := "akka-persistence-inmemory"

organization := "com.github.dnvriend"

version := "1.3.14"

scalaVersion := "2.11.8"

crossScalaVersions := Seq("2.11.8", "2.12.0")

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies ++= {
  val akkaVersion = "2.4.12"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-query-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "org.scalaz" %% "scalaz-core" % "7.2.7",
    "com.datastax.cassandra" % "cassandra-driver-core" % "3.1.2",
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion % Test,
    "ch.qos.logback" % "logback-classic" % "1.1.7" % Test,
    "com.typesafe.akka" %% "akka-persistence-tck" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
    "org.scalatest" %% "scalatest" % "3.0.0" % Test
  )
}

fork in Test := true

parallelExecution in Test := false

licenses += ("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php"))

// enable scala code formatting //
import com.typesafe.sbt.SbtScalariform

import scalariform.formatter.preferences._

// Scalariform settings
SbtScalariform.autoImport.scalariformPreferences := SbtScalariform.autoImport.scalariformPreferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)

// enable updating file headers //
import de.heikoseeberger.sbtheader.license.Apache2_0

headers := Map(
  "scala" -> Apache2_0("2016", "Dennis Vriend"),
  "conf" -> Apache2_0("2016", "Dennis Vriend", "#")
)

enablePlugins(AutomateHeaderPlugin)