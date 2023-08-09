import sbt._
import sbt.Keys._
import bintray.{BintrayKeys, BintrayPlugin}

object PublishSettings extends AutoPlugin with BintrayKeys {
  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin && sbtrelease.ReleasePlugin && BintrayPlugin && ProjectSettings

 override def projectSettings = Seq(
    publishMavenStyle := true,
    pomExtraSetting("pekko-persistence-inmemory"),
    homepageSetting("pekko-persistence-inmemory"),
    bintrayPackageLabelsSettings("inmemory"),
    bintrayPackageAttributesSettings("pekko-persistence-inmemory")
 )
  
def pomExtraSetting(name: String) = pomExtra := 
    <scm>
        <url>https://github.com/dnvriend/${name}</url>
        <connection>scm:git@github.com:alstanchev/${name}.git</connection>
        </scm>
        <developers>
        <developer>
            <id>alstanchev</id>
            <name>Aleksandar Stanchev</name>
            <url>https://github.com/alstanchev</url>
        </developer>
        </developers>

    def homepageSetting(name: String) = 
      homepage := Some(url(s"https://github.com/alstanchev/$name"))

    def bintrayPackageLabelsSettings(labels: String*) = 
	  bintrayPackageLabels := Seq("pekko", "persistence") ++ labels

    def bintrayPackageAttributesSettings(name: String) = {
      bintrayPackageAttributes ~= (_ ++ Map(
          "website_url" -> Seq(bintry.Attr.String(s"https://github.com/alstanchev/$name")),
          "github_repo" -> Seq(bintry.Attr.String(s"https://github.com/alstanchev/$name.git")),
          "issue_tracker_url" -> Seq(bintry.Attr.String(s"https://github.com/alstanchev/$name.git/issues/"))
        ))
    }
}