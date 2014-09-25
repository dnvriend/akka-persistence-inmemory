# akka-persistence-inmemory
Akka-persistence-inmemory is a plugin for [akka-persistence](http://doc.akka.io/docs/akka/snapshot/scala/persistence.html) 
that writes journal and snapshot entries entries to an in-memory store. It is very useful for testing your persistent actors.

# Dependency
To include the plugin into your sbt project, add the following lines to your build.sbt file:

    resolvers += "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"

    libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.0.0"

# Configuration
Add the following to the application.conf:

```
akka {
  persistence {
    journal.plugin = "inmemory-journal"
    snapshot-store.plugin = "inmemory-snapshot-store"
  }
}
```

## What's new?

## 1.0.0
 - Moved to bintray

## 0.0.2
 - Akka 2.3.4 -> 2.3.6

## 0.0.1
 - Initial Release

Have fun!