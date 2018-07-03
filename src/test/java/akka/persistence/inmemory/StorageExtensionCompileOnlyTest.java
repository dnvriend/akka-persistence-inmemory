package akka.persistence.inmemory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Status;
import akka.persistence.inmemory.extension.InMemoryJournalStorage;
import akka.persistence.inmemory.extension.InMemorySnapshotStorage;
import akka.persistence.inmemory.extension.StorageExtension;
import akka.persistence.inmemory.extension.StorageExtensionImpl;
import akka.testkit.TestProbe;

public class StorageExtensionCompileOnlyTest {

    public void shouldHaveANiceJavaAPI() {
        ActorSystem actorSystem = ActorSystem.create();
        TestProbe tp = new TestProbe(actorSystem);
        StorageExtensionImpl extension = StorageExtension.get(actorSystem);

        InMemoryJournalStorage.ClearJournal clearJournal = InMemoryJournalStorage.clearJournal();
        ActorRef actorRef = extension.journalStorage();
        tp.send(actorRef, clearJournal);
        tp.expectMsg(new Status.Success(""));

        InMemorySnapshotStorage.ClearSnapshots clearSnapshots = InMemorySnapshotStorage.clearSnapshots();
        tp.send(actorRef, clearSnapshots);
        tp.expectMsg(new Status.Success(""));
    }
}
