/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.inmemory.snapshot

import akka.persistence.serialization.Snapshot
import akka.persistence.inmemory.TestSpec
import akka.persistence.inmemory.dao.SnapshotDao.SnapshotData
import akka.persistence.inmemory.serialization.{ AkkaSerializationProxy, MockSerializationProxy }
import akka.persistence.{ SelectedSnapshot, SnapshotMetadata }

class InMemorySnapshotStoreMapperTest extends TestSpec() {

  it should "mapToSelectedSnapshot with mock serializer" in {
    val snapshot = Snapshot("")
    val serializationProxy = MockSerializationProxy(snapshot)
    val serializedSnapshot: Array[Byte] = serializationProxy.serialize(snapshot).success.value
    val data = SnapshotData("pid", 1L, 1L, serializedSnapshot)

    InMemorySnapshotStoreLike.mapToSelectedSnapshot(data, serializationProxy).success.value shouldBe
      SelectedSnapshot(SnapshotMetadata(data.persistenceId, data.sequenceNumber, data.created), snapshot.data)
  }

  it should "mapToSelectedSnapshot with AkkaSerializerProxy" in {
    val snapshot = Snapshot("")
    val serializationProxy = AkkaSerializationProxy(serialization)
    val serializedSnapshot: Array[Byte] = serializationProxy.serialize(snapshot).success.value
    val data = SnapshotData("pid", 1L, 1L, serializedSnapshot)

    InMemorySnapshotStoreLike.mapToSelectedSnapshot(data, serializationProxy).success.value shouldBe
      SelectedSnapshot(SnapshotMetadata(data.persistenceId, data.sequenceNumber, data.created), snapshot.data)
  }
}
