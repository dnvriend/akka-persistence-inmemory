import bintray.Plugin._

seq(bintraySettings:_*)

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("akka", "persistence", "in-memory")

bintray.Keys.packageAttributes in bintray.Keys.bintray ~=
  ((_: bintray.AttrMap) ++ Map("website_url" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-inmemory.git")), "github_repo" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-inmemory.git")), "issue_tracker_url" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-inmemory.git/issues/"))))