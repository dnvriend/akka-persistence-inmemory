# akka-persistence-inmemory
Akka-persistence-inmemory is a plugin for akka-persistence that writes journal and snapshot entries entries to an in-memory store. It is very useful for testing your persistent actors.

Service | Status | Description
------- | ------ | -----------
License | [![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) | Apache 2.0
Bintray | [![Download](https://api.bintray.com/packages/dnvriend/maven/akka-persistence-inmemory/images/download.svg) ](https://bintray.com/dnvriend/maven/akka-persistence-inmemory/_latestVersion) | Latest Version on Bintray

## New release
The latest version is `v1.2.9`

- It uses the same codebase as [akka-persistence-jdbc](https://github.com/dnvriend/akka-persistence-jdbc) but uses an immutable Map as the storage engine.
- It relies on [Akka Serialization](http://doc.akka.io/docs/akka/2.4.1/scala/serialization.html),
  - For serializing, please split the domain model from the storage model, and use a binary format for the storage model that support schema versioning like [Google's protocol buffers](https://developers.google.com/protocol-buffers/docs/overview), as it is used by Akka Persistence, and is available as a dependent library. For an example on how to use Akka Serialization with protocol buffers, you can examine the [akka-serialization-test](https://github.com/dnvriend/akka-serialization-test) study project,
- It supports the `Persistence Query` interface for both Java and Scala thus providing a universal asynchronous stream based query interface,

## Installation
Add the following to your `build.sbt`:

```scala
// the library is available in Bintray's JCenter
resolvers += Resolver.jcenterRepo

libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.2.9"
```

## Configuration
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

## How to get the ReadJournal using Scala
The `ReadJournal` is retrieved via the `akka.persistence.query.PersistenceQuery` extension:

```scala
import akka.persistence.query.PersistenceQuery
import akka.persistence.inmemory.query.journal.scaladsl.InMemoryReadJournal
 
val readJournal: InMemoryReadJournal = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)
```

## How to get the ReadJournal using Java
The `ReadJournal` is retrieved via the `akka.persistence.query.PersistenceQuery` extension:

```java
import akka.persistence.query.PersistenceQuery
import akka.persistence.inmemory.query.journal.javadsl.InMemoryReadJournal

final InMemoryReadJournal readJournal = PersistenceQuery.get(system).getReadJournalFor(InMemoryReadJournal.class, InMemoryReadJournal.Identifier());
```

## Persistence Query
The plugin supports the following queries:

## AllPersistenceIdsQuery and CurrentPersistenceIdsQuery
`allPersistenceIds` and `currentPersistenceIds` are used for retrieving all persistenceIds of all persistent actors.

```scala
import akka.actor.ActorSystem
import akka.stream.{Materializer, ActorMaterializer}
import akka.stream.scaladsl.Source
import akka.persistence.query.PersistenceQuery
import akka.persistence.inmemory.query.journal.scaladsl.InMemoryReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: InMemoryReadJournal = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)

val willNotCompleteTheStream: Source[String, NotUsed] = readJournal.allPersistenceIds()

val willCompleteTheStream: Source[String, NotUsed] = readJournal.currentPersistenceIds()
```

The returned event stream is unordered and you can expect different order for multiple executions of the query.

When using the `allPersistenceIds` query, the stream is not completed when it reaches the end of the currently used persistenceIds, 
but it continues to push new persistenceIds when new persistent actors are created. 

When using the `currentPersistenceIds` query, the stream is completed when the end of the current list of persistenceIds is reached,
thus it is not a `live` query.

The stream is completed with failure if there is a failure in executing the query in the backend journal.

## EventsByPersistenceIdQuery and CurrentEventsByPersistenceIdQuery
`eventsByPersistenceId` and `currentEventsByPersistenceId` is used for retrieving events for 
a specific PersistentActor identified by persistenceId.

```scala
import akka.actor.ActorSystem
import akka.stream.{Materializer, ActorMaterializer}
import akka.stream.scaladsl.Source
import akka.persistence.query.{ PersistenceQuery, EventEnvelope }
import akka.persistence.inmemory.query.journal.scaladsl.InMemoryReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: InMemoryReadJournal = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)

val willNotCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.eventsByPersistenceId("some-persistence-id", 0L, Long.MaxValue)

val willCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.currentEventsByPersistenceId("some-persistence-id", 0L, Long.MaxValue)
```

You can retrieve a subset of all events by specifying `fromSequenceNr` and `toSequenceNr` or use `0L` and `Long.MaxValue` respectively to retrieve all events. Note that the corresponding sequence number of each event is provided in the `EventEnvelope`, which makes it possible to resume the stream at a later point from a given sequence number.

The returned event stream is ordered by sequence number, i.e. the same order as the PersistentActor persisted the events. The same prefix of stream elements (in same order) are returned for multiple executions of the query, except for when events have been deleted.

The stream is completed with failure if there is a failure in executing the query in the backend journal.

## EventsByTag and CurrentEventsByTag
`eventsByTag` and `currentEventsByTag` are used for retrieving events that were marked with a given 
`tag`, e.g. all domain events of an Aggregate Root type.

```scala
import akka.actor.ActorSystem
import akka.stream.{Materializer, ActorMaterializer}
import akka.stream.scaladsl.Source
import akka.persistence.query.{ PersistenceQuery, EventEnvelope }
import akka.persistence.inmemory.query.journal.scaladsl.InMemoryReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: InMemoryReadJournal = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)

val willNotCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.eventsByTag("apple", 0L)

val willCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.currentEventsByTag("apple", 0L)
```

To tag events you'll need to create an [Event Adapter](http://doc.akka.io/docs/akka/2.4.1/scala/persistence.html#event-adapters-scala) 
that will wrap the event in a [akka.persistence.journal.Tagged](http://doc.akka.io/api/akka/2.4.1/#akka.persistence.journal.Tagged) 
class with the given tags. The `Tagged` class will instruct the persistence plugin to tag the event with the given set of tags.
The persistence plugin will __not__ store the `Tagged` class in the journal. It will strip the `tags` and `payload` from the `Tagged` class,
and use the class only as an instruction to tag the event with the given tags and store the `payload` in the 
`message` field of the journal table. 

```scala
import akka.persistence.journal.{ Tagged, WriteEventAdapter }
import com.github.dnvriend.Person.{ LastNameChanged, FirstNameChanged, PersonCreated }

class TaggingEventAdapter extends WriteEventAdapter {
  override def manifest(event: Any): String = ""

  def withTag(event: Any, tag: String) = Tagged(event, Set(tag))

  override def toJournal(event: Any): Any = event match {
    case _: PersonCreated ⇒
      withTag(event, "person-created")
    case _: FirstNameChanged ⇒
      withTag(event, "first-name-changed")
    case _: LastNameChanged ⇒
      withTag(event, "last-name-changed")
    case _ ⇒ event
  }
}
```

The `EventAdapter` must be registered by adding the following to the root of `application.conf` Please see the 
[demo-akka-persistence-jdbc](https://github.com/dnvriend/demo-akka-persistence-jdbc) project for more information.

```bash
jdbc-journal {
  event-adapters {
    tagging = "com.github.dnvriend.TaggingEventAdapter"
  }
  event-adapter-bindings {
    "com.github.dnvriend.Person$PersonCreated" = tagging
    "com.github.dnvriend.Person$FirstNameChanged" = tagging
    "com.github.dnvriend.Person$LastNameChanged" = tagging
  }
}
```

You can retrieve a subset of all events by specifying offset, or use 0L to retrieve all events with a given tag. 
The offset corresponds to an ordered sequence number for the specific tag. Note that the corresponding offset of each 
event is provided in the EventEnvelope, which makes it possible to resume the stream at a later point from a given offset.

In addition to the offset the EventEnvelope also provides persistenceId and sequenceNr for each event. The sequenceNr is 
the sequence number for the persistent actor with the persistenceId that persisted the event. The persistenceId + sequenceNr 
is an unique identifier for the event.

The returned event stream contains only events that correspond to the given tag, and is ordered by the creation time of the events, 
The same stream elements (in same order) are returned for multiple executions of the same query. Deleted events are not deleted 
from the tagged event stream. 

## EventsByPersistenceIdAndTag and CurrentEventsByPersistenceIdAndTag
`eventsByPersistenceIdAndTag` and `currentEventsByPersistenceIdAndTag` is used for retrieving specific events identified 
by a specific tag for a specific PersistentActor identified by persistenceId. These two queries basically are 
convenience operations that optimize the lookup of events because the database can efficiently filter out the initial 
persistenceId/tag combination. 

```scala
import akka.actor.ActorSystem
import akka.stream.{Materializer, ActorMaterializer}
import akka.stream.scaladsl.Source
import akka.persistence.query.{ PersistenceQuery, EventEnvelope }
import akka.persistence.inmemory.query.journal.scaladsl.InMemoryReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: InMemoryReadJournal = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)

val willNotCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.eventsByPersistenceIdAndTag("fruitbasket", "apple", 0L)

val willCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.currentEventsByPersistenceIdAndTag("fruitbasket", "apple", 0L)
```

# What's new?
## 1.2.10 (2016-03-17)
  - Refactored the akka-persistence-query interfaces, integrated it back again in one jar, for jcenter deployment simplicity

## 1.2.9 (2016-03-16)
  - Added the appropriate Maven POM resources to be publishing to Bintray's JCenter

## 1.2.8 (2016-03-03)
  - Fix for propagating serialization errors to akka-persistence so that any error regarding the persistence of messages will be handled by the callback handler of the Persistent Actor; `onPersistFailure`.

## 1.2.7 (2016-02-18)
  - Better storage implementation for journal and snapshot

## 1.2.6 (2016-02-17)
  - Akka 2.4.2-RC3 -> 2.4.2

## 1.2.5 (2016-02-13)
  - akka-persistence-jdbc-query 1.0.0 -> 1.0.1

## 1.2.4 (2016-02-13)
  - Akka 2.4.2-RC2 -> 2.4.2-RC3

## 1.2.3 (2016-02-08)
  - Compatibility with Akka 2.4.2-RC2
  - Refactored the akka-persistence-query extension interfaces to its own jar: `"com.github.dnvriend" %% "akka-persistence-jdbc-query" % "1.0.0"`

## 1.2.2 (2016-01-30)
  - Code is based on [akka-persistence-jdbc](https://github.com/dnvriend/akka-persistence-jdbc)
  - Supports the following queries:
    - `allPersistenceIds` and `currentPersistenceIds`
    - `eventsByPersistenceId` and `currentEventsByPersistenceId`
    - `eventsByTag` and `currentEventsByTag`
    - `eventsByPersistenceIdAndTag` and `currentEventsByPersistenceIdAndTag`
  
## 1.2.1 (2016-01-28)
  - Supports for the javadsl query API

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
