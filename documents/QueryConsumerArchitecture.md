# Query Consumer Refactor
The pekko persistence query api is based upon a push approach in where persisted events will be pushed
downstream to any subscribers of the log. This rather naive approach has as a side effect that there has to
be some kind of subscription management in pekko-persistence. At the moment there is none available in 
pekko persistence and so the current design must change.

# Looking at other log architectures
There are other log architectures, for example [Apache Kafka](http://kafka.apache.org/), that use a clean separation
of concerns [design](https://kafka.apache.org/08/design.html) that consists of one or more producers that write to a log, 
the broker itself that manages the log, and one or more consumers that read from the log. This separation makes it possible 
to look at the problem of each component in isolation and create a design that focuses on the single responsibility that it has.

So when looking only at the consumer of the kafka log, [the consumer](https://kafka.apache.org/08/design.html#theconsumer)
has the following responsibilities:
 
 * pulling data from the broker by issuing 'fetch' requests to the brokers what to consume,
 * specifying its position in the log with each request,
 * handing the response, which consists of a chunk of data beginning from the log position,
 * issuing the maximum number of messages the client can handle by means of a back pressure mechanism
 
# Refactoring the pekko persistence in-memory query clients
When looking at the design of the Pekko Persistence, the client api are also fully separate of the producer 
of the log. When we copy the design of Kafka, the following changes to the design to the pekko persistence
in-memory query api clients can be made:

 * active clients that `pull` from the broker by issuing `fetch` requests,
 * handling the response that conform to the [reactive streams](http://www.reactive-streams.org/) specification,
 * specifying `the position` in the log with each request with a `maximum number of messages`,
 * delivering the messages to any subscribers of the stream.
 
This design places the responsibility of consuming the log and making sure that messages are send to any waiting subscribers 
of the the log solely on the consumer API and decouples the producer from the consumer. The producer (here pekko-persistence) 
must be changed because it does not have to take consumers into account. It has a single responsibility, batch writing messages
to the log. 

This design change has an impact to the design and will be reflected in a change to the source code. 