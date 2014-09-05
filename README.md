# akka-persistence-inmemory
Akka-persistence-inmemory is a plugin for [akka-persistence](http://doc.akka.io/docs/akka/snapshot/scala/persistence.html) 
that writes journal and snapshot entries entries to an in-memory store. It is very useful for testing your persistent actors.

# Installation
To include the plugin into your sbt project, add the following lines to your build.sbt file:

    libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "0.0.2"

For Maven users, add the following to the pom.xml

    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-inmemory_2.10</artifactId>
        <version>0.0.2</version>
    </dependency>
    
    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-inmemory_2.11</artifactId>
        <version>0.0.2</version>
    </dependency>

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

## 0.0.2
 - Akka 2.3.4 -> 2.3.6

## 0.0.1
 - Initial Release

Have fun!