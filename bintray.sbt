// enable publishing to jcenter
homepage := Some(url("https://github.com/dnvriend/akka-persistence-inmemory"))

pomIncludeRepository := (_ => false)

pomExtra := <scm>
  <url>https://github.com/dnvriend/akka-persistence-inmemory</url>
  <connection>scm:git@github.com:dnvriend/akka-persistence-inmemory.git</connection>
  </scm>
  <developers>
    <developer>
      <id>dnvriend</id>
      <name>Dennis Vriend</name>
      <url>https://github.com/dnvriend</url>
    </developer>
  </developers>

publishMavenStyle := true

bintrayPackageLabels := Seq("akka", "persistence", "inmemory")

bintrayPackageAttributes ~=
  (_ ++ Map(
    "website_url" -> Seq(bintry.Attr.String("https://github.com/dnvriend/akka-persistence-inmemory")),
    "github_repo" -> Seq(bintry.Attr.String("https://github.com/dnvriend/akka-persistence-inmemory.git")),
    "issue_tracker_url" -> Seq(bintry.Attr.String("https://github.com/dnvriend/akka-persistence-inmemory.git/issues/"))
  )
)

//bintrayRepository := "maven"
