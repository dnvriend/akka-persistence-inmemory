import com.jsuereth.sbtpgp.PgpKeys.useGpg
import sbt._
import sbt.Keys._
import xerial.sbt.Sonatype.autoImport._

object PublishSettings extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin && ProjectSettings

  override def projectSettings = Seq(
    organization := "io.github.alstanchev", // This sets the groupId
    name := "pekko-persistence-inmemory", // This sets the artifactId base
    homepage := Some(url(s"https://github.com/alstanchev/${name.value}")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "alstanchev",
        "Aleksandar Stanchev",
        "aleksandar.stanchev@bosch.com",
        url("https://github.com/alstanchev/pekko-persistence-inmemory")
      )
    ),
    // Sonatype settings
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
    ThisBuild / useGpg := true,
    // Publishing destination
    publishTo := {
      val nexus = "https://s01.oss.sonatype.org/"
      if (version.value.endsWith("-SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  )

}
