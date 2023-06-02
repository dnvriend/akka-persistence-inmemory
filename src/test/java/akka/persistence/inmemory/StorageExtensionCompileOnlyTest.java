package akka.persistence.inmemory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Status;
import akka.persistence.inmemory.extension.*;
import akka.testkit.TestProbe;

public class StorageExtensionCompileOnlyTest {

    public void shouldHaveANiceJavaAPI() {
        ActorSystem actorSystem = ActorSystem.create();
        TestProbe tp = new TestProbe(actorSystem);
//        StorageExtension extension = StorageExtensionProvider.get(actorSystem);
//
//        InMemoryJournalStorage.ClearJournal clearJournal = InMemoryJournalStorage.clearJournal();
//        ActorRef actorRef = extension.journalStorage(actorSystem.settings().config());
//        tp.send(actorRef, clearJournal);
//        tp.expectMsg(new Status.Success(""));
//
//        InMemorySnapshotStorage.ClearSnapshots clearSnapshots = InMemorySnapshotStorage.clearSnapshots();
//        tp.send(actorRef, clearSnapshots);
//        tp.expectMsg(new Status.Success(""));
    }
}
