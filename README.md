# pekko-persistence-inmemory

[![Join the chat at https://app.gitter.im/#/room/#pekko-persistence-inmemory:gitter.im](https://badges.gitter.im/repo.svg)](https://app.gitter.im/#/room/#pekko-persistence-inmemory:gitter.im)
[![Build Status](https://github.com/alstanchev/pekko-persistence-inmemory/actions/workflows/build.yml/badge.svg)](https://github.com/alstanchev/pekko-persistence-inmemory/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.alstanchev/pekko-persistence-inmemory_2.13)](https://central.sonatype.com/search?q=pekko-persistence-inmemory&namespace=io.github.alstanchev)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

[pekko-persistence-inmemory](https://github.com/alstanchev/pekko-persistence-inmemory) is a plugin for pekko-persistence that stores journal and snapshot messages in memory, which is very useful when testing persistent actors, persistent FSM and pekko cluster.

## Installation
```text
// the library is available in OSSSonatype and MavenCentral repository

    <dependency>
        <groupId>com.github.alstanchev</groupId>
        <artifactId>pekko-persistence-inmemory_2.13</artifactId>
        <version>1.0.1</version>
    </dependency>
```


```scala
// the library is available in OSSSonatype and MavenCentral repository

// pekko 1.0.x
libraryDependencies += "io.github.alstanchev" %% "pekko-persistence-inmemory" % "1.0.1"
```

## Contribution policy
Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## License

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Configuration
Add the following to the application.conf:

```scala
pekko {
  persistence {
    journal.plugin = "inmemory-journal"
    snapshot-store.plugin = "inmemory-snapshot-store"
  }
}
```

## Configuring the query API
The query API can be configured by overriding the defaults by placing the following in application.conf:
 
```
inmemory-read-journal {
  # Absolute path to the write journal plugin configuration section to get the event adapters from
  write-plugin = "inmemory-journal"

  # there are two modes; sequence or uuid. If set to "sequence" and NoOffset will be requested, then
  # the query will return Sequence offset types. If set to "uuid" and NoOffset will be requested, then
  # the query will return TimeBasedUUID offset types. When the query is called with Sequence then
  # the query will return Sequence offset types and if the query is called with TimeBasedUUID types then
  # the query will return TimeBasedUUID offset types.
  offset-mode = "sequence"

  # ask timeout on Futures
  ask-timeout = "10s"

  # New events are retrieved (polled) with this interval.
  refresh-interval = "100ms"

  # How many events to fetch in one query (replay) and keep buffered until they
  # are delivered downstreams.
  max-buffer-size = "100"
}
```

## Clearing Journal and Snapshot messages
It is possible to manually clear the journal and snapshot storage, for example:

```scala
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.persistence.inmemory.extension.{ InMemoryJournalStorage, InMemorySnapshotStorage, StorageExtension }
import org.apache.pekko.testkit.TestProbe
import org.scalatest.{ BeforeAndAfterEach, Suite }

trait InMemoryCleanup extends BeforeAndAfterEach { _: Suite =>

  implicit def system: ActorSystem

  override protected def beforeEach(): Unit = {
    val tp = TestProbe()
    tp.send(StorageExtension(system).journalStorage, InMemoryJournalStorage.ClearJournal)
    tp.expectMsg(org.apache.pekko.actor.Status.Success(""))
    tp.send(StorageExtension(system).snapshotStorage, InMemorySnapshotStorage.ClearSnapshots)
    tp.expectMsg(org.apache.pekko.actor.Status.Success(""))
    super.beforeEach()
  }
}
```

From Java:

```java
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.persistence.inmemory.InMemoryJournalStorage
import org.apache.pekko.persistence.inmemory.InMemorySnapshotStorage
        

    ActorRef actorRef = extension.journalStorage();
    
    InMemoryJournalStorage.ClearJournal clearJournal = InMemoryJournalStorage.clearJournal();
    tp.send(actorRef, clearJournal);
    tp.expectMsg(new Status.Success(""));
    
    InMemorySnapshotStorage.ClearSnapshots clearSnapshots = InMemorySnapshotStorage.clearSnapshots();
    tp.send(actorRef, clearSnapshots);
    tp.expectMsg(new Status.Success(""));
```

## offset-mode
pekko-persistence-query introduces `org.apache.pekko.persistence.query.Offset`, an ADT that defines `org.apache.pekko.persistence.query.NoOffset`,
`org.apache.pekko.persistence.query.Sequence` and `org.apache.pekko.persistence.query.TimeBasedUUID`. These offsets can be used when using the
queries `org.apache.pekko.persistence.query.scaladsl.EventsByTagQuery` and `org.apache.pekko.persistence.query.scaladsl.CurrentEventsByTagQuery`
to request and offset in the stream of events.

Because pekko-persistence-inmemory implements both the Sequence-based number offset strategy as the TimeBasedUUID strategy
it is required to configure the `inmemory-read-journal.offset-mode="sequence"`. This way pekko-persistence-inmemory knows
what kind of journal it should emulate when a NoOffset type is requested. EventEnvelope will contain either a Sequence
when the configuration is `sequence` or a TimeBasedUUID when the configuration is `uuid`.

By default, the setting is `sequence`.

## query and event-adapters
Write plugins (i.e. pekko-persistence-plugins that write events) can define event adapters. These event adapters can be
reused when executing a query so that the EventEnvelope contains the `application domain event` and not the data-model
representation of that event. Set the  `inmemory-read-journal.write-plugin="inmemory-journal"` and configure it with the
write plugin name (defaults to the `inmemory-journal`).

## Refresh Interval
The async query API uses polling to query the journal for new events. The refresh interval can be configured
e.g. "1s" so that the journal will be polled every 1 second. This setting is global for each async query, so
the _allPersistenceId_, _eventsByTag_ and _eventsByPersistenceId_ queries.

## Max Buffer Size
When an async query is started, a number of events will be buffered and will use memory when not consumed by 
a Sink. The default size is 100.

## How to get the ReadJournal using Scala
The `ReadJournal` is retrieved via the `org.apache.pekko.persistence.query.PersistenceQuery` extension:

```scala
import org.apache.pekko.persistence.query.scaladsl._

lazy val readJournal = PersistenceQuery(system).readJournalFor("inmemory-read-journal")
 .asInstanceOf[ReadJournal
    with CurrentPersistenceIdsQuery
    with AllPersistenceIdsQuery
    with CurrentEventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with EventsByPersistenceIdQuery
    with EventsByTagQuery]
```

## How to get the ReadJournal using Java
The `ReadJournal` is retrieved via the `org.apache.pekko.persistence.query.PersistenceQuery` extension:

```java
import org.apache.pekko.persistence.query.PersistenceQuery
import org.apache.pekko.persistence.inmemory.query.journal.javadsl.InMemoryReadJournal

final InMemoryReadJournal readJournal = PersistenceQuery.get(system).getReadJournalFor(InMemoryReadJournal.class, InMemoryReadJournal.Identifier());
```

## Persistence Query
The plugin supports the following queries:

## AllPersistenceIdsQuery and CurrentPersistenceIdsQuery
`allPersistenceIds` and `currentPersistenceIds` are used for retrieving all persistenceIds of all persistent actors.

```scala
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{Materializer, ActorMaterializer}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.persistence.query.PersistenceQuery
import org.apache.pekko.persistence.inmemory.query.journal.scaladsl.InMemoryReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: InMemoryReadJournal = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)

val willNotCompleteTheStream: Source[String, NotUsed] = readJournal.allPersistenceIds()

val willCompleteTheStream: Source[String, NotUsed] = readJournal.currentPersistenceIds()
```

The returned event stream is unordered, and you can expect different order for multiple executions of the query.

When using the `allPersistenceIds` query, the stream is not completed when it reaches the end of the currently used persistenceIds, 
but it continues to push new persistenceIds when new persistent actors are created. 

When using the `currentPersistenceIds` query, the stream is completed when the end of the current list of persistenceIds is reached,
thus it is not a `live` query.

The stream is completed with failure if there is a failure in executing the query in the backend journal.

## EventsByPersistenceIdQuery and CurrentEventsByPersistenceIdQuery
`eventsByPersistenceId` and `currentEventsByPersistenceId` is used for retrieving events for 
a specific PersistentActor identified by persistenceId.

```scala
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{Materializer, ActorMaterializer}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.persistence.query.scaladsl._

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)

lazy val readJournal = PersistenceQuery(system).readJournalFor("inmemory-read-journal")
 .asInstanceOf[ReadJournal
    with CurrentPersistenceIdsQuery
    with AllPersistenceIdsQuery
    with CurrentEventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with EventsByPersistenceIdQuery
    with EventsByTagQuery]

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
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{Materializer, ActorMaterializer}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.persistence.query.scaladsl._

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)

lazy val readJournal = PersistenceQuery(system).readJournalFor("inmemory-read-journal")
 .asInstanceOf[ReadJournal
    with CurrentPersistenceIdsQuery
    with AllPersistenceIdsQuery
    with CurrentEventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with EventsByPersistenceIdQuery
    with EventsByTagQuery]

val willNotCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.eventsByTag("apple", 0L)

val willCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.currentEventsByTag("apple", 0L)
```

To tag events you'll need to create an [Event Adapter](https://pekko.apache.org/docs/pekko/current/typed/persistence.html#event-adapters) 
that will wrap the event in an [org.apache.pekko.persistence.journal.Tagged](https://pekko.apache.org/api/pekko/current/org/apache/pekko/persistence/journal/Tagged.html) 
class with the given tags. The `Tagged` class will instruct the persistence plugin to tag the event with the given set of tags.
The persistence plugin will __not__ store the `Tagged` class in the journal. It will strip the `tags` and `payload` from the `Tagged` class,
and use the class only as an instruction to tag the event with the given tags and store the `payload` in the 
`message` field of the journal table. 

```scala
import org.apache.pekko.persistence.journal.{ Tagged, WriteEventAdapter }
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
[demo-akka-persistence-jdbc](https://github.com/dnvriend/demo-akka-persistence-jdbc) project for more information (it is not ported to Apache Pekko but is from the upstream forked project's author and is useful for information). The identifier of the persistence plugin must be used which for the inmemory plugin is `inmemory-journal`. 

```bash
inmemory-journal {
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
is a unique identifier for the event.

The returned event stream contains only events that correspond to the given tag, and is ordered by the creation time of the events, 
The same stream elements (in same order) are returned for multiple executions of the same query. Deleted events are not deleted 
from the tagged event stream. 

## Storage extension
You can change the default storage to store a journal by defined property keys using this configuration. 
This can be useful to configure a behavior similar to cassandra key spaces.
```
# the storage in use
inmemory-storage {
  # storage using inmemory journal for each different value for the configured property keys
  class = "org.apache.pekko.persistence.inmemory.extension.StorageExtensionByProperty"
  # property keys in journal plugin configuration, for each different value a own journal will be stored
  property-keys = ["keyspace"]
}
```

### 1.0.1 (2023-09-11)
 - Renamed packages

### 1.0.0 (2023-08-24)
 - Fork from [dnvriend/akka-persistence-inmemory:2.5.15.2](https://github.com/dnvriend/akka-persistence-inmemory)
 - Akka replaced with [Pekko 1.0.1](https://github.com/apache/incubator-pekko)
 - Scala 2.12.x, 2.13.x
