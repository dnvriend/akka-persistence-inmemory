# akka-persistence-inmemory
Akka-persistence-inmemory is a plugin for [akka-persistence](http://doc.akka.io/docs/akka/snapshot/scala/persistence.html) 
that writes journal and snapshot entries entries to an in-memory store. It is very useful for testing your persistent actors.

Service | Status | Description
------- | ------ | -----------
License | [![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) | Apache 2.0
Travis (master) | [![Build Status](https://travis-ci.org/dnvriend/akka-persistence-inmemory.svg?branch=master)](https://travis-ci.org/dnvriend/akka-persistence-inmemory) | master branch test
Codacy | [![Codacy Badge](https://api.codacy.com/project/badge/2cedef156eaf441fbe867becfc5fcb24)](https://www.codacy.com/app/dnvriend/akka-persistence-inmemory) | Code Quality
Bintray | [ ![Download](https://api.bintray.com/packages/dnvriend/maven/akka-persistence-inmemory/images/download.svg) ](https://bintray.com/dnvriend/maven/akka-persistence-inmemory/_latestVersion) | Latest Version on Bintray

# Repository
To include the plugin into your project, add the following lines to your build file:

## SBT

```
resolvers += "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"
```

## Maven

```
<repository>
  <snapshots><enabled>false</enabled></snapshots>
  <id>central</id>
  <name>bintray</name>
  <url>http://dl.bintray.com/dnvriend/maven</url>
</repository>
```

## Gradle

```
repositories {
    maven {
        url "http://dl.bintray.com/dnvriend/maven"
    }
}
```

## Latest stable release for Akka 2.3.x  

### SBT

```scala
resolvers += "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"

libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.0.5"
```

### Maven

```
<dependency>
    <groupId>com.github.dnvriend</groupId>
    <artifactId>akka-persistence-inmemory_2.10</artifactId>
    <version>1.0.5</version>
</dependency>

<dependency>
    <groupId>com.github.dnvriend</groupId>
    <artifactId>akka-persistence-inmemory_2.11</artifactId>
    <version>1.0.5</version>
</dependency>
```

### Gradle

```
dependencies {
    compile 'com.github.dnvriend:akka-persistence-inmemory_2.10:1.0.5' // Scala 2.10    
    compile 'com.github.dnvriend:akka-persistence-inmemory_2.11:1.0.5' // Scala 2.11 and above
}
```

## Latest stable release for Akka 2.4.x

### SBT

```scala
resolvers += "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"

libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.1.5"
```

### Maven

```
<dependency>
    <groupId>com.github.dnvriend</groupId>
    <artifactId>akka-persistence-inmemory_2.11</artifactId>
    <version>1.1.5</version>
</dependency>
```

### Gradle

```
dependencies {
    compile 'com.github.dnvriend:akka-persistence-inmemory_2.11:1.1.5'
}
```

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

# Persistence Query for the Inmemory Plugin
Please note that persistence queries are only available in version `1.1.3` and up.
 
## How to get the ReadJournal
The `ReadJournal` is retrieved via the `akka.persistence.query.PersistenceQuery` extension:

```
import akka.persistence.query.PersistenceQuery
import akka.persistence.inmemory.query.InMemoryReadJournal
 
val queries = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)
```

# Supported Queries

## CurrentEventsByPersistenceIdQuery
`currentEventsByPersistenceIdQuery` is used for retrieving events for a specific `PersistentActor` identified by `persistenceId`.

```
implicit val mat = ActorMaterializer()(system)
val queries = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)
 
val src: Source[EventEnvelope, Unit] =
  queries.eventsByPersistenceId("some-persistence-id", 0L, Long.MaxValue)
 
val events: Source[Any, Unit] = src.map(_.event)
```

You can retrieve a subset of all events by specifying fromSequenceNr and toSequenceNr or use 0L and Long.MaxValue respectively to retrieve all events. Note that the corresponding sequence number of each event is provided in the EventEnvelope, which makes it possible to resume the stream at a later point from a given sequence number.

The returned event stream is ordered by sequence number, i.e. the same order as the PersistentActor persisted the events. The same prefix of stream elements (in same order) are returned for multiple executions of the query, except for when events have been deleted.

The query supports two different completion modes:

The stream is completed when it reaches the end of the currently stored events.

## CurrentPersistenceIds
`currentPersistenceIds` is used for retrieving all persistenceIds of all persistent actors. 

```
implicit val mat = ActorMaterializer()(system)
val queries = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)
 
val src: Source[String, Unit] = queries.currentPersistenceIds()
```

The returned event stream is unordered. The stream is completed when it reaches the end of the currently stored persistenceIds.

# AllPersistenceIdsQuery
`allPersistenceIdsQuery` is used for retrieving all persistenceIds of all persistent actors. It does exactly the same as the `currentPersistenceIds` query, except the stream does not complete.

```
implicit val mat = ActorMaterializer()(system)
val queries = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)
 
val src: Source[String, Unit] = queries.allPersistenceIdsQuery()
```

The returned event stream is unordered, the stream will only complete when there are no persistenceIds or the stream
 has explicitly been canceled by the subscriber:
 
```
implicit val mat = ActorMaterializer()(system)
val queries = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)
 
val src: Source[String, Unit] = queries.allPersistenceIdsQuery()

// cancel / terminate the stream
src.cancel()
```

# EventsByPersistenceIdQuery
`eventsByPersistenceId` is used for retrieving events for a specific `PersistentActor` identified by `persistenceId`.

```
implicit val mat = ActorMaterializer()(system)
val queries = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)
 
val src: Source[EventEnvelope, Unit] =
  queries.eventsByPersistenceId("some-persistence-id", 0L, Long.MaxValue)
 
val events: Source[Any, Unit] = src.map(_.event)
```

You can retrieve a subset of all events by specifying `fromSequenceNr` and `toSequenceNr` or use `0L` and `Long.MaxValue` respectively to retrieve all events. Note that the corresponding sequence number of each event is provided in the `EventEnvelope`, which makes it possible to resume the stream at a later point from a given sequence number.

The returned event stream is ordered by sequence number, i.e. the same order as the `PersistentActor` persisted the events. The same prefix of stream elements (in same order) are returned for multiple executions of the query, except for when events have been deleted.

The stream is not completed when it reaches the end of the currently stored events, but it continues to push new events when new events are persisted. 

# CurrentEventsByPersistenceIdQuery
`currentEventsByPersistenceId` does the same as `eventsByPersistenceId` with the only difference that it completes the stream when it reaches the end of the currently stored events.

# What's new?

## 1.2.0 (2016-01-26)
  - Compatibility with Akka 2.4.2-RC1 

## 1.1.6 (2015-12-02)
 - Compatibility with Akka 2.4.1
 - Merged PR #17 [Evgeny Shepelyuk](https://github.com/eshepelyuk) Upgrade to AKKA 2.4.1, thanks!

## 1.1.5 (2015-10-24)
 - Compatibility with Akka 2.4.0
 - Merged PR #13 [Evgeny Shepelyuk](https://github.com/eshepelyuk) HighestSequenceNo should be kept on message deletion, thanks!
 - Should be a fix for [Issue #13 - HighestSequenceNo should be kept on message deletion](https://github.com/dnvriend/akka-persistence-inmemory/issues/13) as per [Akka issue #18559](https://github.com/akka/akka/issues/18559) 

## 1.1.4 (2015-10-17)
 - Compatibility with Akka 2.4.0
 - Merged PR #12 [Evgeny Shepelyuk](https://github.com/eshepelyuk) Live version of eventsByPersistenceId, thanks!
 
## 1.1.3 (2015-10-02)
 - Compatibility with Akka 2.4.0
 - Akka 2.4.0-RC3 -> 2.4.0
 
## 1.1.3-RC3 (2015-09-24)
 - Merged PR #10 [Evgeny Shepelyuk](https://github.com/eshepelyuk) Live version of allPersistenceIds, thanks!
 - Compatibility with Akka 2.4.0-RC3
 - Use the following library dependency: `"com.github.dnvriend" %% "akka-persistence-inmemory" % "1.1.3-RC3"` 

## 1.1.1-RC3 (2015-09-19)
 - Merged Issue #9 [Evgeny Shepelyuk](https://github.com/eshepelyuk) Initial implemenation of Persistence Query for In Memory journal, thanks!
 - Compatibility with Akka 2.4.0-RC3
 - Use the following library dependency: `"com.github.dnvriend" %% "akka-persistence-inmemory" % "1.1.1-RC3"` 

## 1.1.0-RC3 (2015-09-17)
 - Merged Issue #6 [Evgeny Shepelyuk](https://github.com/eshepelyuk) Conditional ability to perform full serialization while adding messages to journal, thanks!
 - Compatibility with Akka 2.4.0-RC3
 - Use the following library dependency: `"com.github.dnvriend" %% "akka-persistence-inmemory" % "1.1.0-RC3"` 

## 1.1.0-RC2 (2015-09-05)
 - Compatibility with Akka 2.4.0-RC2
 - Use the following library dependency: `"com.github.dnvriend" %% "akka-persistence-inmemory" % "1.1.0-RC2"` 

## 1.0.5 (2015-09-04)
 - Compatibilty with Akka 2.3.13
 - Akka 2.3.12 -> 2.3.13

## 1.1.0-RC1 (2015-09-02)
 - Compatibility with Akka 2.4.0-RC1
 - Use the following library dependency: `"com.github.dnvriend" %% "akka-persistence-inmemory" % "1.1.0-RC1"` 
   
## 1.0.4 (2015-08-16)
 - Scala 2.11.6 -> 2.11.7
 - Akka 2.3.11 -> 2.3.12
 - Apache-2.0 license
       
## 1.0.3 (2015-05-25)
 - Merged Issue #2 [Sebastián Ortega](https://github.com/sortega) Regression: Fix corner case when persisted events are deleted, thanks!
 - Added test for the corner case issue #1 and #2

## 1.0.2 (2015-05-20)
 - Refactored from the ConcurrentHashMap implementation to a pure Actor managed concurrency model

## 1.0.1 (2015-05-16)
 - Some refactoring, fixed some misconceptions about the behavior of Scala Futures one year ago :)
 - Akka 2.3.6 -> 2.3.11
 - Scala 2.11.1 -> 2.11.6
 - Scala 2.10.4 -> 2.10.5
 - Merged Issue #1 [Sebastián Ortega](https://github.com/sortega) Fix corner case when persisted events are deleted, thanks!

## 1.0.0
 - Moved to bintray

## 0.0.2
 - Akka 2.3.4 -> 2.3.6

## 0.0.1
 - Initial Release

# Code of Conduct
**Contributors all agree to follow the [W3C Code of Ethics and Professional Conduct](http://www.w3.org/Consortium/cepc/).**

If you want to take action, feel free to contact Dennis Vriend <dnvriend@gmail.com>. You can also contact W3C Staff as explained in [W3C Procedures](http://www.w3.org/Consortium/pwe/#Procedures).

# License
This source code is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0). The [quick summary of what this license means is available here](https://tldrlegal.com/license/apache-license-2.0-(apache-2.0))

Have fun!
