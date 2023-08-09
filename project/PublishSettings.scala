import sbt._
import sbt.Keys._
import sbtrelease.ReleasePlugin.autoImport.releaseVcsSign

object PublishSettings extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin && sbtrelease.ReleasePlugin && ProjectSettings

  override def projectSettings = Seq(
    publishMavenStyle := true,
    publishTo := Some(
      if (isSnapshot.value)
//        Resolver.file("file", new File("target/snapshots"))
        "GitHub Package Registry Snapshots" at s"https://maven.pkg.github.com/alstanchev/${name.value}"
      else
        "GitHub Package Registry Releases" at s"https://maven.pkg.github.com/alstanchev/${name.value}"
//        "GitHub Packages" at s"https://maven.pkg.github.com/alstanchev/${name.value}"
    ),
    credentials += Credentials(
      "GitHub Package Registry",
      "maven.pkg.github.com",
      sys.env.getOrElse("USER", ""),
      sys.env.getOrElse("PACKAGES_TOKEN", "")
    ),
    releaseVcsSign := true,
    pomExtraSetting("pekko-persistence-inmemory"),
    homepageSetting("pekko-persistence-inmemory")
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
}
