# akka-persistence-inmemory
Akka-persistence-inmemory is a plugin for [akka-persistence](http://doc.akka.io/docs/akka/snapshot/scala/persistence.html) 
that writes journal entries to an in-memory store. It supports writing journal messages and snapshots so its very useful
for testing your persistent actors.

# Installation
To include the plugin into your sbt project, add the following lines to your build.sbt file:

    libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "0.0.1"

For Maven users, add the following to the pom.xml

    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-inmemory_2.10</artifactId>
        <version>0.0.1</version>
    </dependency>
    
    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-inmemory_2.11</artifactId>
        <version>0.0.1</version>
    </dependency>

This version of akka-persistence-inmemory depends on Akka 2.3.4 and is cross-built against Scala 2.10.4 and 2.11.2
and should be binary compatible with Akka 2.3.5

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

### What's new?

### 0.0.1
 - Initial Release

Have fun!